package org.dsa.iot.spark

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD.rddToPairRDDFunctions
import org.apache.spark.streaming.{ Seconds, StreamingContext }
import org.apache.spark.streaming.dstream.DStream.toPairDStreamFunctions
import org.apache.spark.util.StatCounter
import org.slf4j.LoggerFactory

/**
 * An sample flow using DSA Receiver.
 */
object ExampleFlow extends App {
  import DSAConnector._

  val log = LoggerFactory.getLogger(getClass)

  protected lazy val sc = new SparkContext("local[*]", "dslink-test")
  protected lazy val ssc = new StreamingContext(sc, Seconds(2))

  val stream1 = ssc.receiverStream(new DSAReceiver(
    "/downstream/System/Memory_Usage",
    "/downstream/System/CPU_Usage"))
  val stream2 = ssc.receiverStream(new DSAReceiver(
    "/downstream/System/Disk_Usage",
    "/downstream/System/Used_Memory"))

  val combined = (stream1 union stream2) map { x =>
    (x._1.stripPrefix("/downstream/System/"), x._3)
  }

  val aggregates = combined mapValues toDouble window (10 seconds) transform { rdd =>
    rdd.aggregateByKey(new StatCounter)(_ merge _, _ merge _)
  }

  aggregates print

  aggregates foreachRDD (_ foreach {
    case (name, stats) =>
      updateNode(s"/output/$name/count", stats.count)
      updateNode(s"/output/$name/mean", stats.mean)
      updateNode(s"/output/$name/min", stats.min)
      updateNode(s"/output/$name/max", stats.max)
      updateNode(s"/output/$name/sum", stats.sum)
      updateNode(s"/output/$name/variance", stats.variance)
      updateNode(s"/output/$name/stdev", stats.stdev)
  })

  ssc.start
  ssc.awaitTermination
}