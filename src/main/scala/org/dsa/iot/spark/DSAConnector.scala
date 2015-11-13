package org.dsa.iot.spark

import java.util.concurrent.CountDownLatch
import scala.util.Try
import org.dsa.iot.dslink.{ DSLink, DSLinkFactory, DSLinkHandler }
import org.dsa.iot.dslink.node.value.Value
import org.dsa.iot.dslink.provider.{ HttpProvider, WsProvider }
import org.dsa.iot.dslink.util.log.LogManager
import org.dsa.iot.spark.netty.{ CustomHttpProvider, CustomWsProvider }
import org.slf4j.LoggerFactory
import com.typesafe.config.ConfigFactory
import org.dsa.iot.spark.logging.Log4jBridge

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

  def updateNode(path: String, value: Value): Unit = {
    val node = responderLink.getNodeManager.getNode(path, true).getNode
    node.setValueType(value.getType)
    node.setValue(value)
  }

  def updateNode(path: String, value: Any): Unit = updateNode(path, anyToValue(value))
}