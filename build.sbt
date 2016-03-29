// properties

val APP_VERSION = "0.3.0-SNAPSHOT"
val SCALA_VERSION = "2.11.7"
val SCALA_DSA_VERSION = "0.2.0"
val SPARK_VERSION = "1.5.1"

// settings

name := "sdk-dslink-scala-spark"

organization := "org.iot-dsa"

version := APP_VERSION

scalaVersion := SCALA_VERSION

crossScalaVersions := Seq("2.10.5", "2.11.7")

scalacOptions ++= Seq("-feature", "-unchecked", "-deprecation", "-Xlint", 
	"-Ywarn-dead-code", "-language:_", "-target:jvm-1.7", "-encoding", "UTF-8")

scalacOptions in (Compile, doc) ++= Seq("-no-link-warnings")

run in Compile <<= Defaults.runTask(fullClasspath in Compile, mainClass in (Compile, run), runner in (Compile, run))

// scoverage options

coverageMinimum := 80
coverageFailOnMinimum := true

// publishing options

publishMavenStyle := true
publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}
pomIncludeRepository := { _ => false }
pomExtra := (
  <url>https://github.com/IOT-DSA/sdk-dslink-scala-spark</url>
  <licenses>
    <license>
      <name>The Apache License, Version 2.0</name>
      <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>scm:git:https://github.com/IOT-DSA/sdk-dslink-scala-spark.git</url>
    <connection>scm:git:git@github.com:IOT-DSA/sdk-dslink-scala-spark.git</connection>
  </scm>
  <developers>
    <developer>
      <id>snark</id>
      <name>Vlad Orzhekhovskiy</name>
      <email>vlad@uralian.com</email>
      <url>http://uralian.com</url>
    </developer>
  </developers>)

pgpSecretRing := file("local.secring.gpg")

pgpPublicRing := file("local.pubring.gpg")

// dependencies
	
libraryDependencies ++= Seq(
  "org.apache.spark"   %% "spark-core"              % SPARK_VERSION   % "provided",
  "org.apache.spark"   %% "spark-streaming"         % SPARK_VERSION   % "provided",
  "org.iot-dsa"        %% "sdk-dslink-scala"        % SCALA_DSA_VERSION
  		exclude("com.fasterxml.jackson.core", "*"),   		
  "org.scalatest"      %% "scalatest"               % "2.2.1"         % "test",
  "org.scalacheck"     %% "scalacheck"              % "1.12.1"        % "test",
  "org.mockito"         % "mockito-core"            % "1.10.19"       % "test"  
)
