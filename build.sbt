// properties

val APP_VERSION = "0.2.0-SNAPSHOT"

val SCALA_VERSION = "2.10.5"

val DSA_VERSION = "0.12.0"

val SPARK_VERSION = "1.5.1"

// settings

name := "sdk-dslink-scala-spark"

organization := "com.uralian"

version := APP_VERSION

enablePlugins(JavaAppPackaging)

scalaVersion := SCALA_VERSION

scalacOptions ++= Seq("-feature", "-unchecked", "-deprecation", "-Xlint", 
	"-Ywarn-dead-code", "-language:_", "-target:jvm-1.7", "-encoding", "UTF-8")

run in Compile <<= Defaults.runTask(fullClasspath in Compile, mainClass in (Compile, run), runner in (Compile, run))
	
libraryDependencies ++= Seq(
  "org.slf4j"           % "slf4j-log4j12"           % "1.6.1",
  "org.apache.spark"   %% "spark-core"              % SPARK_VERSION   % "provided",
  "org.apache.spark"   %% "spark-streaming"         % SPARK_VERSION   % "provided",
  "org.iot-dsa"         % "dslink"                  % DSA_VERSION
  		exclude("org.slf4j", "*")
  		exclude("io.netty", "*"),
  "org.scalatest"      %% "scalatest"               % "2.2.1"         % "test",
  "org.scalacheck"     %% "scalacheck"              % "1.12.1"        % "test"  
)
