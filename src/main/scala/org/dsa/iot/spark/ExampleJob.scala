package org.dsa.iot.spark

import scala.concurrent.{ Await, Future }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

import org.apache.spark.SparkContext
import org.slf4j.LoggerFactory

/**
 * A sample Spark batch job using DSA Connector.
 */
object ExampleJob extends App {
  import DSAConnector._

  val log = LoggerFactory.getLogger(getClass)

  protected lazy val sc = new SparkContext("local[*]", "dslink-stream-test")

  val path = "/sys"
  val fdata = for {
    allNodes <- getNodeChildren(path)
    paths = allNodes filter (_.getValueType != null) map (_.getPath)
    values <- Future.sequence(getNodeValues(paths.toSet))
  } yield values

  val data = Await.result(fdata, Duration.Inf)
  val rdd = sc.parallelize(data.toSeq)

  rdd foreach println
}
