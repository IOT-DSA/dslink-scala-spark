package org.dsa.iot.spark

import org.scalacheck.{ Gen, Prop }
import org.scalatest.{ Finders, Matchers, Suite, WordSpecLike }
import org.scalatest.prop.Checkers
import org.dsa.iot.dslink.node.value.Value
import org.dsa.iot.dslink.util.json.JsonObject
import collection.JavaConverters._
import scala.collection.mutable.HashMap
import org.dsa.iot.dslink.util.json.JsonArray

class ConversionSpec extends Suite with WordSpecLike with Matchers with Checkers {

  "valueToAny" should {
    "recognize boolean" in valueToAny(new Value(true)) === true
    "recognize integer" in valueToAny(new Value(5)) === 5
    "recognize double" in valueToAny(new Value(5.5)) === 5.5
    "recognize map" in {
      val m = Map[String, Object]("a" -> "b")
      valueToAny(new Value(new JsonObject(m.asJava))) === m
    }
    "recognize list" in {
      val l = List("a", "b", "c")
      valueToAny(new Value(new JsonArray(l.asJava))) === l
    }
  }
  
  "anyToValue" should {
    "handle null" in anyToValue(null) === null
    "handle int" in anyToValue(5) === new Value(5)
    "handle double" in anyToValue(5.5) === new Value(5.5)
    "handle boolean" in anyToValue(false) === new Value(false)
    "handle string" in anyToValue("abc") === new Value("abc")
    "handle map" in {
      val m = Map[String, Object]("a" -> "b")
      anyToValue(m) === new Value(new JsonObject(m.asJava))
    }
    "handle list" in {
      val l = List("a", "b", "c")
      anyToValue(l) === new Value(new JsonArray(l.asJava))
    }
  }

  "toInt" should {
    "return correct value for Ints" in
      Prop.forAll { (a: Int) => toInt(a) === a }
    "return correct value for Longs" in
      Prop.forAll { (a: Long) => toInt(a) === a.toInt }
    "return correct value for Shorts" in
      Prop.forAll { (a: Short) => toInt(a) === a.toInt }
    "return correct value for Bytes" in
      Prop.forAll { (a: Byte) => toInt(a) === a.toInt }
    "return correct value for Floats" in
      Prop.forAll { (a: Float) => toInt(a) === a.toInt }
    "return correct value for Doubles" in
      Prop.forAll { (a: Double) => toInt(a) === a.toInt }
    "throw an exception on non-numerical arguments" in {
      an[IllegalArgumentException] should be thrownBy toInt("abc")
    }
  }

  "toFloat" should {
    "return correct value for Ints" in
      Prop.forAll { (a: Int) => toFloat(a) === a.toFloat }
    "return correct value for Longs" in
      Prop.forAll { (a: Long) => toFloat(a) === a.toFloat }
    "return correct value for Shorts" in
      Prop.forAll { (a: Short) => toFloat(a) === a.toFloat }
    "return correct value for Bytes" in
      Prop.forAll { (a: Byte) => toFloat(a) === a.toFloat }
    "return correct value for Floats" in
      Prop.forAll { (a: Float) => toFloat(a) === a.toFloat }
    "return correct value for Doubles" in
      Prop.forAll { (a: Double) => toFloat(a) === a.toFloat }
    "throw an exception on non-numerical arguments" in {
      an[IllegalArgumentException] should be thrownBy toFloat("abc")
    }
  }
}