package org.dsa.iot

import java.util.Date

import scala.{ BigDecimal, BigInt }
import scala.collection.JavaConverters._

import org.apache.spark.streaming.{ Milliseconds, Minutes, Seconds }
import org.dsa.iot.dslink.node.value.{ Value, ValueType }
import org.dsa.iot.dslink.util.json.{ JsonArray, JsonObject }

/**
 * DSA Spark helper types and functions.
 */
package object spark {

  /**
   * The data type emitted by the DSAReceiver, which includes the path, the timestamp,
   * and the actual value.
   */
  type TimedValue = (String, Date, Any)

  /**
   * Extracts the data from a Value object.
   */
  def valueToAny(value: Value): Any = value.getType.toJsonString match {
    case ValueType.JSON_BOOL   => value.getBool
    case ValueType.JSON_NUMBER => value.getNumber
    case ValueType.JSON_MAP    => value.getMap.getMap.asScala
    case ValueType.JSON_ARRAY  => value.getArray.getList.asScala.toList
    case _                     => value.getString
  }

  /**
   * Converts a value into Value object.
   */
  def anyToValue(value: Any): Value = value match {
    case null                => null
    case x: java.lang.Number => new Value(x)
    case x: Boolean          => new Value(x)
    case x: String           => new Value(x)
    case x: Map[_, _]        => new Value(new JsonObject(x.asInstanceOf[Map[String, Object]].asJava))
    case x: List[_]          => new Value(new JsonArray(x.asJava))
    case x @ _               => new Value(x.toString)
  }

  /*
   * Converters that transform Any argument to a numeric type (if possible).
   * They will throw an IllegalArgumentException, if the argument is not a number.
   */

  def toInt = toJavaNumber _ andThen javaNumberToInt _
  def toLong = toJavaNumber _ andThen javaNumberToLong _
  def toShort = toJavaNumber _ andThen javaNumberToShort _
  def toByte = toJavaNumber _ andThen javaNumberToByte _
  def toFloat = toJavaNumber _ andThen javaNumberToFloat _
  def toDouble = toJavaNumber _ andThen javaNumberToDouble _
  def toBigDecimal = (x: Any) => BigDecimal(toJavaNumber(x).doubleValue)
  def toBigInteger = (x: Any) => BigInt(toJavaNumber(x).intValue)
  def toBoolean = (x: Any) => x.asInstanceOf[Boolean]
  def toMap = (x: Any) => x.asInstanceOf[Map[String, Any]]
  def toList = (x: Any) => x.asInstanceOf[List[Any]]

  private def toJavaNumber(x: Any) = x match {
    case n: java.lang.Number => n
    case _                   => throw new IllegalArgumentException(s"Not a number: $x")
  }

  private def javaNumberToInt(n: java.lang.Number) = n.intValue
  private def javaNumberToLong(n: java.lang.Number) = n.longValue
  private def javaNumberToShort(n: java.lang.Number) = n.shortValue
  private def javaNumberToByte(n: java.lang.Number) = n.byteValue
  private def javaNumberToFloat(n: java.lang.Number) = n.floatValue
  private def javaNumberToDouble(n: java.lang.Number) = n.doubleValue

  /**
   * A convenience class to create spark durations.
   */
  implicit class RichInt(val interval: Int) extends AnyVal {
    def seconds = Seconds(interval)
    def milliseconds = Milliseconds(interval)
    def minutes = Minutes(interval)
  }
}