package org.dsa.iot.spark

import scala.collection.JavaConverters.{ asScalaBufferConverter, mapAsJavaMapConverter, mapAsScalaMapConverter, seqAsJavaListConverter }

import org.dsa.iot.dslink.node.value.Value
import org.dsa.iot.dslink.util.json.{ JsonArray, JsonObject }
import org.scalacheck.Prop
import org.scalatest.{ Matchers, Suite, WordSpecLike }
import org.scalatest.prop.Checkers

class ConversionSpec extends Suite with WordSpecLike with Matchers with Checkers {

  "valueToAny" should {
    "recognize boolean" in {
      valueToAny(new Value(true)) shouldBe true
    }
    "recognize integer" in {
      valueToAny(new Value(5)) shouldBe 5
    }
    "recognize double" in {
      valueToAny(new Value(5.5)) shouldBe 5.5
    }
    "recognize map" in {
      val m = Map[String, Object]("a" -> "b")
      valueToAny(new Value(new JsonObject(m.asJava))) shouldBe m
    }
    "recognize list" in {
      val l = List("a", "b", "c")
      valueToAny(new Value(new JsonArray(l.asJava))) shouldBe l
    }
    "recognize nested lists" in {
      val l1 = List("a", 1, true)
      val l2 = List("x", new JsonArray(l1.asJava), new JsonArray(l1.reverse.asJava))
      val l3 = List(new JsonArray(l2.asJava), new JsonArray(l1.asJava), "abc", new JsonArray())
      val value = new Value(new JsonArray(l3.asJava))
      valueToAny(value) shouldBe List(List("x", l1, l1.reverse), l1, "abc", List())
    }
    "recognize nested maps" in {
      val m1 = Map[String, Object]("a" -> "b", "c" -> 5.asInstanceOf[Object])
      val m2 = Map[String, Object]("x" -> "abc", "m1" -> new JsonObject(m1.asJava))
      val m3 = Map[String, Object]("m2" -> new JsonObject(m2.asJava), "m1" -> new JsonObject(m1.asJava))
      val value = new Value(new JsonObject(m3.asJava))
      valueToAny(value) shouldBe Map("m2" -> Map("x" -> "abc", "m1" -> m1), "m1" -> m1)
    }
    "recognized combined nested structures" in {
      val l1 = List("a", 1, true)
      val m1 = Map[String, Object]("a" -> "b", "l1" -> new JsonArray(l1.asJava))
      val l2 = List(new JsonObject(m1.asJava), new JsonArray(l1.asJava))
      val value = new Value(new JsonArray(l2.asJava))
      valueToAny(value) shouldBe List(Map("a" -> "b", "l1" -> l1), l1)
    }
  }

  "anyToValue" should {
    "handle null" in {
      anyToValue(null) shouldBe null
    }
    "handle int" in {
      anyToValue(5) shouldBe new Value(5)
    }
    "handle double" in {
      anyToValue(5.5) shouldBe new Value(5.5)
    }
    "handle boolean" in {
      anyToValue(false) shouldBe new Value(false)
    }
    "handle string" in {
      anyToValue("abc") shouldBe new Value("abc")
    }
    "handle map" in {
      val m = Map[String, Object]("a" -> "b")
      anyToValue(m).getMap.getMap shouldBe new JsonObject(m.asJava).getMap
    }
    "handle list" in {
      val l = List("a", "b", "c")
      anyToValue(l).getArray.getList shouldBe new JsonArray(l.asJava).getList
    }
    "handle nested structures" in {
      val x = List("a", Map("b" -> "c", "d" -> List("1", "2", Map("3" -> "4"))))
      val list = anyToValue(x).getArray.getList
      list.size shouldBe 2
      list.get(0) shouldBe "a"
      list.get(1) shouldBe a[JsonObject]
      val obj = list.get(1).asInstanceOf[JsonObject]
      obj.size shouldBe 2
      obj.getMap.asScala should contain("b" -> "c")
      obj.getMap.asScala("d") shouldBe a[JsonArray]
      val arr = obj.getMap.asScala("d").asInstanceOf[JsonArray].getList.asScala
      arr should contain inOrder ("1", "2")
      arr.size shouldBe 3
      arr(2) shouldBe a[JsonObject]
      arr(2).asInstanceOf[JsonObject].getMap.asScala shouldBe Map("3" -> "4")
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