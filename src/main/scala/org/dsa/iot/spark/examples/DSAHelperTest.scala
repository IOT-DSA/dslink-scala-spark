package org.dsa.iot.spark.examples

import java.util.UUID

import scala.collection.JavaConverters.mapAsScalaMapConverter
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.util.{ Failure, Random, Success }

import org.dsa.iot.dslink.node.value.ValueType
import org.dsa.iot.spark.{ DSAConnector, DSAHelper, RichNodeBuilder, createAction, toParameter }

/**
 * Examples using DSAHelper functions.
 */
object DSAHelperTest extends App {

  implicit val requester = DSAConnector.requesterLink.getRequester
  implicit val responder = DSAConnector.responderLink.getResponder

  // create a new dataflow using Observable
  val flowName1 = UUID.randomUUID().toString
  val obsCreate = DSAHelper.invoke("/downstream/dataflow/createDataflow", "name" -> flowName1)
  obsCreate subscribe (
    onNext = _ => {},
    onError = err => println("Error creating a dataflow: " + err),
    onCompleted = () => println(s"New dataflow created at /downstream/dataflow/$flowName1"))

  // create a new dataflow using Future
  val flowName2 = UUID.randomUUID().toString
  val frsp = DSAHelper.invokeAndWait("/downstream/dataflow/createDataflow", "name" -> flowName2)
  frsp.onComplete {
    case Success(_) => println(s"New dataflow created at /downstream/dataflow/$flowName2")
    case Failure(_) => println("Error creating dataflow")
  }
  Await.ready(frsp, Duration.Inf)

  // export flow
  val obsExport = obsCreate flatMap (_ => DSAHelper invoke s"/downstream/dataflow/$flowName1/exportDataflow")
  obsExport subscribe (
    onNext = rsp => println("Exported dataflow: " + rsp.getTable.getRows.get(0).getValues.get(0).getMap))

  // removing dataflows
  obsExport flatMap (_ => DSAHelper invoke s"/downstream/dataflow/$flowName1/deleteDataflow") subscribe (
    onCompleted => println(s"Dataflow $flowName1 successfully deleted"),
    onError => println(s"Error deleting dataflow $flowName1"))
  DSAHelper invokeAndWait s"/downstream/dataflow/$flowName2/deleteDataflow" onComplete {
    case Success(_) => println(s"Dataflow $flowName2 successfully deleted")
    case Failure(_) => println(s"Error deleting dataflow $flowName2")
  }

  // list nodes under /downstream/System
  val obsSystem = DSAHelper list "/downstream/System"
  obsSystem subscribe (rsp => rsp.getUpdates.asScala foreach {
    case (node, flag) => println(s"""${node.getPath}${if (flag) " REMOVED" else ""}""")
  })

  // subscribe to updates for CPU and Memory usage
  val obsCpu = DSAHelper watch "/downstream/System/CPU_Usage"
  val obsCpu2 = DSAHelper watch "/downstream/System/CPU_Usage"
  val obsMem = DSAHelper watch "/downstream/System/Memory_Usage"
  val sub1 = obsCpu merge obsMem subscribe (sv => println(sv.getPath + " : " + sv.getValue))
  val sub2 = obsCpu2 subscribe (sv => println(sv.getPath + " : " + sv.getValue))

  // wait, then unsubscribe from MEM and one of CPU threads
  Thread sleep 3000
  sub1 unsubscribe

  // wait, then unsubscribe from the other CPU thread
  Thread sleep 2000
  sub2 unsubscribe

  // get children of /downstream
  DSAHelper getNodeChildren "/downstream" subscribe (node => println(node.getPath))

  // get values of System nodes
  for {
    of <- DSAHelper getNodeValue "/downstream/System/Open_Files"
    pl <- DSAHelper getNodeValue "/downstream/System/Platform"
    du <- DSAHelper getNodeValue "/downstream/System/Disk_Usage"
  } yield println(s"""System info:
    |  Open files: ${of._3}
    |  Platform: ${pl._3}
    |  Disk usage: ${du._3}
    """.stripMargin)

  // create and update own nodes
  val root = responder.getDSLink.getNodeManager.getSuperRoot
  val outNode = root createChild "out" build

  val c1 = outNode createChild "aaaa" display "Aaaa" valueType ValueType.STRING build

  val c2 = outNode createChild "bbbb" display "Bbbb" valueType ValueType.NUMBER build

  outNode createChild "setAaaa" display "Update Aaaa" action (createAction(_ => {
    DSAHelper updateNode "/out/aaaa" -> Random.nextInt(1000).toString
  })) build

  outNode createChild "setBbbb" display "Update Bbbb" action (
    createAction(
      parameters = List("value" -> ValueType.NUMBER),
      handler = result => {
        val value = result.getParameter("value").getNumber
        DSAHelper updateNode "/out/bbbb" -> value
      })) build
}