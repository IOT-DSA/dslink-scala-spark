package org.dsa.iot.spark

import java.util.concurrent.CountDownLatch

import scala.annotation.migration
import scala.collection.JavaConverters.{ mapAsScalaMapConverter, setAsJavaSetConverter }
import scala.concurrent.{ Future, Promise }
import scala.util.Try

import org.dsa.iot.dslink.node.Node
import org.dsa.iot.dslink.{ DSLink, DSLinkFactory, DSLinkHandler }
import org.dsa.iot.dslink.methods.requests.ListRequest
import org.dsa.iot.dslink.methods.responses.ListResponse
import org.dsa.iot.dslink.node.value.{ SubscriptionValue, Value }
import org.dsa.iot.dslink.provider.{ HttpProvider, WsProvider }
import org.dsa.iot.dslink.util.SubData
import org.dsa.iot.dslink.util.handler.Handler
import org.dsa.iot.dslink.util.log.LogManager
import org.dsa.iot.spark.logging.Log4jBridge
import org.dsa.iot.spark.netty.{ CustomHttpProvider, CustomWsProvider }
import org.slf4j.LoggerFactory

import com.typesafe.config.ConfigFactory

/**
 * Connects to a DSA broker and exposes requester and responder links.
 */
object DSAConnector extends DSLinkHandler {

  LogManager.setBridge(Log4jBridge)
  HttpProvider.setProvider(new CustomHttpProvider)
  WsProvider.setProvider(new CustomWsProvider)

  private val log = LoggerFactory.getLogger(getClass)

  private val latch = new CountDownLatch(2)

  private lazy val args = {
    val cfg = ConfigFactory.load.getConfig("dsa")
    val brokerUrl = Try(cfg.getString("broker.url")) getOrElse "http://localhost:8080/conn"
    Array("-b", brokerUrl)
  }

  lazy val provider = synchronized {
    val p = DSLinkFactory.generate(args, this)
    p.start
    Try(latch.await) getOrElse log.error("latch error")
    p
  }

  lazy val responderLink = {
    provider
    rspLink
  }

  lazy val requesterLink = {
    provider
    reqLink
  }

  @volatile private var rspLink: DSLink = null
  @volatile private var reqLink: DSLink = null

  /* DSLinkHandler API */

  override def isRequester = true

  override val isResponder = true

  override def onResponderInitialized(link: DSLink) = {
    rspLink = link
    log.info("Responder initialized")
  }

  override def onResponderConnected(link: DSLink) = {
    latch.countDown
    log.info("Responder connected")
  }

  override def onRequesterInitialized(link: DSLink) = {
    reqLink = link
    log.info("Requester initialized")
  }

  override def onRequesterConnected(link: DSLink) = {
    latch.countDown
    log.info("Requester connected")
  }

  /* features */

  /**
   * Returns the children of some node as a future of a node list.
   */
  def getNodeChildren(path: String): Future[Iterable[Node]] = {
    val p = Promise[Iterable[Node]]()

    val request = new ListRequest(path)
    requesterLink.getRequester.list(request, new Handler[ListResponse] {
      def handle(event: ListResponse) = {
        val updates = event.getUpdates.asScala
        val children = updates collect {
          case (node, java.lang.Boolean.FALSE) => node
        }
        p.success(children)
      }
    })

    p.future
  }

  /**
   * Reads values from a collection of nodes and returns them as a list of futures.
   */
  def getNodeValues(paths: String*): Iterable[Future[TimedValue]] = getNodeValues(paths.toSet)

  /**
   * Reads values from a collection of nodes and returns them as a list of futures.
   */
  def getNodeValues(paths: Set[String]): Iterable[Future[TimedValue]] = {
    val pp = paths map (p => p -> Promise[TimedValue]()) toMap

    val data = paths map (p => new SubData(p, null)) asJava

    requesterLink.getRequester.subscribe(data, new Handler[SubscriptionValue] {
      def handle(event: SubscriptionValue) = completePromise(pp(event.getPath))(event)
    })

    pp.values map (_.future)
  }

  /**
   * Reads a node value and returns it as a future.
   */
  def getNodeValue(path: String): Future[TimedValue] = {
    val p = Promise[TimedValue]()

    requesterLink.getRequester.subscribe(path, new Handler[SubscriptionValue] {
      def handle(event: SubscriptionValue) = completePromise(p)(event)
    })

    p.future
  }

  /**
   * Updates a node with the supplied value.
   */
  def updateNode(path: String, value: Value): Unit = {
    val node = responderLink.getNodeManager.getNode(path, true).getNode
    node.setValueType(value.getType)
    node.setValue(value)
  }

  /**
   * Updates a node with the supplied value.
   */
  def updateNode(path: String, value: Any): Unit = updateNode(path, anyToValue(value))

  private def completePromise(p: Promise[TimedValue])(event: SubscriptionValue): Unit = {
    requesterLink.getRequester.unsubscribe(event.getPath, null)

    val value = valueToAny(event.getValue)
    val time = event.getValue.getDate
    val item = (event.getPath, time, value)
    p.trySuccess(item)
  }
}