package org.dsa.iot.spark

import scala.util.control.NonFatal

import org.apache.spark.Logging
import org.apache.spark.storage.StorageLevel.MEMORY_AND_DISK
import org.apache.spark.streaming.receiver.Receiver
import org.dsa.iot.TimedValue
import org.dsa.iot.dslink.link.Requester
import org.dsa.iot.dslink.node.value.SubscriptionValue
import org.dsa.iot.dslink.util.handler.Handler
import org.dsa.iot.valueToAny

/**
 * This class is an implementation of Spark receiver that subscribes to the
 * selected DSA nodes and consumes their values, emitting DStream[TimedValue].
 *
 * @author Vlad Orzhekhovskiy
 */
class DSAReceiver(val paths: String*) extends Receiver[TimedValue](MEMORY_AND_DISK) with Logging with Runnable {
  import DSAReceiver._

  require(!paths.isEmpty, "at least one path should be present")
  require(paths.toSet.size == paths.size, "duplicate path found")

  /**
   * Starts the receiver in a new thread.
   */
  def onStart() = new Thread(this, "DSA-Receiver").start

  /**
   * Called by the framework, does nothing.
   */
  def onStop() {}

  /**
   * Subscribes to updates from the DSA and forwards them to Spark engine.
   */
  def run() = try {
    require(requester != null, "Requester not set, use DSAReceiver.setRequester")

    paths foreach { path =>
      logInfo(s"Subscribing to path $path")

      requester.subscribe(path, new Handler[SubscriptionValue] {
        def handle(event: SubscriptionValue) = {
          val value = valueToAny(event.getValue)
          val time = new java.util.Date(event.getValue.getTime)
          val item = (path, time, value)
          logDebug(s"Received update: $item")
          store(item)
        }
      })
    }
  } catch {
    case NonFatal(e) => restart("error receiving data", e)
  }
}

/**
 * Provides the requester object.
 */
object DSAReceiver {
  @transient private var requester: Requester = null

  def setRequester(requester: Requester) = { this.requester = requester }
}