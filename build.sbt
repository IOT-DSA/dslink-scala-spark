// properties

val APP_VERSION = "0.1.0-SNAPSHOT"

val SCALA_VERSION = "2.10.4"

val DSA_VERSION = "0.12.0"

val SPARK_VERSION = "1.5.1"

// settings

name := "dslink-scala-spark"

organization := "com.uralian"

version := APP_VERSION

enablePlugins(JavaAppPackaging)

scalaVersion := SCALA_VERSION

scalacOptions ++= Seq("-feature", "-unchecked", "-deprecation", "-Xlint", 
	"-Ywarn-dead-code", "-language:_", "-target:jvm-1.7", "-encoding", "UTF-8")
	
libraryDependencies ++= Seq(
  "org.slf4j"           % "slf4j-log4j12"           % "1.6.1",
  "org.apache.spark"   %% "spark-core"              % SPARK_VERSION,
  "org.apache.spark"   %% "spark-streaming"         % SPARK_VERSION,
  "org.iot-dsa"         % "dslink"                  % DSA_VERSION
  		exclude("org.slf4j", "*")
  		exclude("io.netty", "*"),
//  "org.iot-dsa"         % "logging"                 % DSA_VERSION
//  		exclude("org.slf4j", "*"),
//  "org.iot-dsa"         % "runtime_shared"          % DSA_VERSION
//  		exclude("org.slf4j", "*"),
//  "com.beust"           % "jcommander"              % "1.48",
//  "org.bouncycastle"    % "bcprov-jdk15on"          % "1.51",  
  "org.scalatest"      %% "scalatest"               % "2.2.1"         % "test",
  "org.scalacheck"     %% "scalacheck"              % "1.12.1"        % "test"  
)
