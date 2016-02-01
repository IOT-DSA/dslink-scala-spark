package org.dsa.iot.spark

import java.io.{ File, PrintWriter }

import scala.io.Source

import org.dsa.iot.{ DSAConnector, Having }

package object examples {

  val DEFAULT_BROKER_URL = "http://localhost:8080/conn"

  /**
   * Creates a new DSAConnector.
   */
  private[examples] def createConnector(args: Array[String]) = {
    val brokerUrl = if (args.length < 1)
      DEFAULT_BROKER_URL having println(s"Broker URL not specified, using the default one: $DEFAULT_BROKER_URL")
    else
      args(0) having (x => println(s"Broker URL: $x"))

    val dslinkJson = copyDslinkJson
    val nodesJsonPath = dslinkJson.getParent + "/nodes.json"
    DSAConnector("-b", brokerUrl, "-d", dslinkJson.getPath, "-n", nodesJsonPath)
  }

  /**
   * Copies the default dslink.json template to a temporary file.
   */
  private[examples] def copyDslinkJson = {
    val source = Source.fromInputStream(getClass.getResourceAsStream("/examples/dslink.json.template"))

    val dslinkFile = File.createTempFile("dslink", ".json")
    dslinkFile.deleteOnExit

    val target = new PrintWriter(dslinkFile)
    source.getLines foreach target.println
    target.close

    dslinkFile
  }
}