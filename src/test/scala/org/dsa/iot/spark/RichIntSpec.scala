package org.dsa.iot.spark

import org.apache.spark.streaming.{ Milliseconds, Minutes, Seconds }
import org.scalacheck.{ Gen, Prop }
import org.scalatest.{ Finders, Matchers, Suite, WordSpecLike }
import org.scalatest.prop.Checkers

class RichIntSpec extends Suite with WordSpecLike with Matchers with Checkers {

  val positive = Gen.posNum[Int]

  "RichInt" should {
    "produce milliseconds" in Prop.forAll(positive) { n => n.milliseconds == Milliseconds(n) }
    "produce seconds" in Prop.forAll(positive) { n => n.seconds == Seconds(n) }
    "produce minutes" in Prop.forAll(positive) { n => n.minutes == Minutes(n) }
  }
}