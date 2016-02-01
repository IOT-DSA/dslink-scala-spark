package org.dsa.iot.spark.examples

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD.rddToPairRDDFunctions
import org.apache.spark.streaming.{ Seconds, StreamingContext }
import org.apache.spark.streaming.dstream.DStream.toPairDStreamFunctions
import org.apache.spark.util.StatCounter
import org.dsa.iot.{ DSAHelper, LinkMode }
import org.dsa.iot.spark.DSAReceiver

/**
 * An sample flow using DSA Receiver.
 */
object SparkFlowTest extends App {

  val connector = createConnector(args)
  val connection = connector start LinkMode.DUAL
  implicit val requester = connection.requester
  implicit val responder = connection.responder

  protected lazy val sc = new SparkContext("local[*]", "dslink-stream-test")
  protected lazy val ssc = new StreamingContext(sc, Seconds(2))

  DSAReceiver.setRequester(requester)

  val stream1 = ssc.receiverStream(new DSAReceiver(
    "/downstream/System/Memory_Usage",
    "/downstream/System/CPU_Usage"))
  val stream2 = ssc.receiverStream(new DSAReceiver(
    "/downstream/System/Disk_Usage",
    "/downstream/System/Used_Memory"))

  val combined = (stream1 union stream2) map { x =>
    (x._1.stripPrefix("/downstream/System/"), x._3)
  }

  val aggregates = combined mapValues {
    case x: java.lang.Number => x.doubleValue
  } window (Seconds(10)) transform { rdd =>
    rdd.aggregateByKey(new StatCounter)(_ merge _, _ merge _)
  }

  aggregates print

  aggregates foreachRDD (_ foreach {
    case (name, stats) =>
      DSAHelper updateNode s"/output/$name/count" -> stats.count
      DSAHelper updateNode s"/output/$name/mean" -> stats.mean
      DSAHelper updateNode s"/output/$name/min" -> stats.min
      DSAHelper updateNode s"/output/$name/max" -> stats.max
      DSAHelper updateNode s"/output/$name/sum" -> stats.sum
      DSAHelper updateNode s"/output/$name/variance" -> stats.variance
      DSAHelper updateNode s"/output/$name/stdev" -> stats.stdev
  })

  ssc.start
  ssc.awaitTermination
}