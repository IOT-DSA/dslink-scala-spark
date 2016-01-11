package org.dsa.iot.spark.examples

import scala.concurrent.ExecutionContext.Implicits.global

import org.apache.spark.SparkContext
import org.dsa.iot.dslink.node.value.ValueType
import org.dsa.iot.spark.{ DSAConnector, DSAHelper }

import rx.lang.scala.Observable

/**
 * A sample Spark batch job using DSA Connector.
 */
object SparkJobTest extends App {

  implicit val requester = DSAConnector.requesterLink.getRequester

  protected lazy val sc = new SparkContext("local[*]", "dslink-batch-test")

  val path = "/sys"
  val values = for {
    node <- DSAHelper getNodeChildren path
    path = node.getPath if node.getValueType == ValueType.NUMBER
    value <- Observable.from(DSAHelper.getNodeValue(path))
  } yield value

  val data = values.take(5).toBlocking.toList
  val rdd = sc parallelize data

  rdd foreach println
}
