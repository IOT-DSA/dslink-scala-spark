package org.dsa.iot.spark.logging

import java.io.File
import org.dsa.iot.dslink.util.log.{ LogBridge, LogLevel }

/**
 * Logging bridge for Log4J.
 */
object Log4jBridge extends LogBridge {
  private var level: LogLevel = null

  def configure(path: File) = {}

  def setLevel(level: LogLevel) = {
    val log4jLevel = level match {
      case LogLevel.DEBUG => org.apache.log4j.Level.DEBUG
      case LogLevel.ERROR => org.apache.log4j.Level.ERROR
      case LogLevel.INFO  => org.apache.log4j.Level.INFO
      case LogLevel.OFF   => org.apache.log4j.Level.OFF
      case LogLevel.TRACE => org.apache.log4j.Level.TRACE
      case LogLevel.WARN  => org.apache.log4j.Level.WARN
    }
    rootLogger.setLevel(log4jLevel)
    this.level = level
  }

  def getLevel = level

  private lazy val rootLogger = org.apache.log4j.Logger.getRootLogger
}