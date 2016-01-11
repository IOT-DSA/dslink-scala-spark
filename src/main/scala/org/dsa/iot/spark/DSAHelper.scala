package org.dsa.iot.spark

import scala.collection.JavaConverters.mapAsScalaMapConverter
import scala.concurrent.{ ExecutionContext, Future, Promise }

import org.dsa.iot.dslink.link.{ Requester, Responder }
import org.dsa.iot.dslink.methods.{ Response, StreamState }
import org.dsa.iot.dslink.methods.requests.{ InvokeRequest, ListRequest, RemoveRequest, SetRequest }
import org.dsa.iot.dslink.methods.responses.{ InvokeResponse, ListResponse, RemoveResponse, SetResponse }
import org.dsa.iot.dslink.node.Node
import org.dsa.iot.dslink.node.value.{ SubscriptionValue, Value }
import org.dsa.iot.dslink.util.handler.Handler
import org.slf4j.LoggerFactory

import rx.lang.scala.{ Observable, Observer, Subscription }

/**
 * Provides methods for executing DSA commands.
 */
object DSAHelper {

  private val log = LoggerFactory.getLogger(getClass)

  private var watches = Map.empty[String, Observable[SubscriptionValue]]

  /* COMMANDS */

  /**
   * Executes Invoke command and returns an Observable that emits InvokeResponse events.
   */
  def invoke(path: String, params: (String, Any)*)(implicit requester: Requester): Observable[InvokeResponse] =
    invoke(path, params.toMap)

  /**
   * Executes Invoke command and returns an Observable that emits InvokeResponse events.
   */
  def invoke(path: String,
             params: Map[String, Any] = Map.empty)(implicit requester: Requester): Observable[InvokeResponse] = {

    val src = Observable.create[InvokeResponse] { observer =>
      val json = mapToJsonObject(params)
      val request = new InvokeRequest(path, json)
      requester.invoke(request, makeHandler(observer))
      log.info(s"API call invoke($path, $params) issued")
      Subscription()
    }

    val oc = src.replay
    oc.subscribe
    oc.connect
    oc
  }

  /**
   * Executes Invoke command and waits until the stream is closed before returning the response.
   */
  def invokeAndWait(path: String, params: (String, Any)*)(implicit requester: Requester): Future[InvokeResponse] =
    invokeAndWait(path, params.toMap)

  /**
   * Executes Invoke command and waits until the stream is closed before returning the response.
   */
  def invokeAndWait(path: String,
                    params: Map[String, Any] = Map.empty)(implicit requester: Requester): Future[InvokeResponse] = {
    val json = mapToJsonObject(params)
    val request = new InvokeRequest(path, json)
    request.setWaitForStreamClose(true)
    execute(requester.invoke, request) having log.info(s"API call invoke($path, $params) issued")
  }

  /**
   * Executes List command and returns an Observable that emits ListResponse events.
   */
  def list(path: String)(implicit requester: Requester) = Observable.create[ListResponse] { observer =>
    val request = new ListRequest(path)
    requester.list(request, makeHandler(observer))
    log.info(s"API call list($path) issued")
    Subscription()
  }

  /**
   * Executes Subscribe command and returns an Observable that emits SubscriptionValue events.
   * The system will remove the watch from a path as soon as the last observer to that path ubsubscribes.
   */
  def watch(path: String)(implicit requester: Requester) = synchronized {
    watches.getOrElse(path, {
      val o = Observable.create[SubscriptionValue] { observer =>
        requester.subscribe(path, makeHandler(observer))
        log.info(s"API call subscribe($path) issued")
        Subscription {
          requester.unsubscribe(path, null)
          log.info(s"API call unsubscribe($path) issued")
        }
      } share

      watches += path -> o
      o
    })
  }

  /**
   * Stops watching the specified path. This will not unsubscribe the existing observers, so it safer to
   * stop watching the path by unsubscribing the observers.
   */
  def unwatch(path: String)(implicit requester: Requester) = synchronized {
    requester.unsubscribe(path, null)
    log.info(s"API call unsubscribe($path) issued")
  }

  /**
   * Sets/publishes the value of the specified node.
   */
  def set(path: String, value: Any)(implicit requester: Requester): Future[SetResponse] =
    set(path, anyToValue(value))

  /**
   * Sets/publishes the value of the specified node.
   */
  def set(path: String, value: Value)(implicit requester: Requester): Future[SetResponse] =
    execute(requester.set, new SetRequest(path, value)) having log.info(s"API call set($path, $value) issued")

  /**
   * Removes attributes/configs of the specified node.
   */
  def remove(path: String)(implicit requester: Requester): Future[RemoveResponse] =
    execute(requester.remove, new RemoveRequest(path)) having log.info(s"API call remove($path) issued")

  /**
   * Closes the stream.
   */
  def close(rid: Int)(implicit requester: Requester) =
    execute(requester.closeStream, rid) having log.info(s"API call closeStream($rid) issued")

  /* NODE OPERATIONS */

  /**
   * Returns the children of some node as Observable.
   */
  def getNodeChildren(path: String)(implicit requester: Requester): Observable[Node] = list(path) flatMap { event =>
    val updates = event.getUpdates.asScala
    val children = updates collect {
      case (node, java.lang.Boolean.FALSE) => node
    }
    Observable.from(children)
  }

  /**
   * Returns the node value as a Future.
   */
  def getNodeValue(path: String)(implicit requester: Requester, ec: ExecutionContext): Future[TimedValue] =
    watch(path).take(1).toBlocking.toFuture map { event =>
      val value = valueToAny(event.getValue)
      val time = new java.util.Date(event.getValue.getTime)
      (event.getPath, time, value)
    }

  /**
   * Updates a node with the supplied value.
   */
  def updateNode(pair: (String, Any))(implicit responder: Responder): Unit =
    updateNode(pair._1, pair._2)

  /**
   * Updates a node with the supplied value.
   */
  def updateNode(path: String, value: Any)(implicit responder: Responder): Unit =
    updateNode(path, anyToValue(value))

  /**
   * Updates a node with the supplied value.
   */
  def updateNode(path: String, value: Value)(implicit responder: Responder): Unit = {
    val node = responder.getDSLink.getNodeManager.getNode(path, true).getNode
    node.setValueType(value.getType)
    node.setValue(value)
  }

  /* AUX methods */

  /**
   * Executes the call and return the future holding the response.
   */
  private def execute[R, T](call: (R, Handler[T]) => Unit, request: R): Future[T] = {
    val p = Promise[T]()
    call(request, new Handler[T] { def handle(event: T) = p.success(event) })
    p.future
  }

  /**
   * Creates a new handler for the specified event type and sends each event to the observer.
   */
  private def makeHandler[T](observer: Observer[T]) = new Handler[T] {
    def handle(event: T) = event match {
      case e: InvokeResponse =>
        if (e.hasError)
          observer.onError(new RuntimeException(e.getError.getMessage))
        else
          observer.onNext(event)
        if (e.getState == StreamState.CLOSED && !e.hasError)
          observer.onCompleted
      case e: Response =>
        if (e.hasError)
          observer.onError(new RuntimeException(e.getError.getMessage))
        else
          observer.onNext(event)
      case _ => observer.onNext(event)
    }
  }
}