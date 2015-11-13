# dslink-scala-spark

A Scala-based DSLink implementation for Apache Spark.

## Features

- `DSAReceiver` class, which can be used to create Spark DStreams from arbitrary nodes in DSA
- `DSAConnector` object, which can be used to communicate with Node API
- DSABroker configuration via `application.conf` file (JSON/HOCON format)
- Recognizes all existing Node API data types

## Usage

To start streaming data from one or more DSA nodes, use `StreamingContext.receiverStream()` method.

```
val sc = new SparkContext(...)
val ssc = new StreamingContext(sc, ...)
val stream = ssc.receiverStream(new DSAReceiver(path1, path2, ..., pathN))  
```
where path1, path2, ..., pathN are paths in the DSA tree.

The type of the returned stream will be `DStream[(String, Date, Any)]` with the elements as:

1. The node path
2. The time of the last update
3. The updated value

If you want stream flow to publish the updates in the node tree, use `DSAConnector.updateNode()` method:

```
stream foreachRDD (_ foreach { point =>
	DSAConnector.updateNode(path1, point.x)
	DSAConnector.updateNode(path2, point.y)
	...
})
```
All paths should be relative to the Spark DSLink's root node.

## Running the application

To run the application, you need to have Java and [SBT](http://www.scala-sbt.org) installed.
If you want to customize the DSA connection parameters, you can either supply your custom 
`application.conf` in the classpath or specify them as JVM arguments with `-D` option. 
```
sbt run -Ddsa.broker.url=http://localhost:8080/conn
``` 

## Distributions

Distributions can be run independently from SBT in any environment with Java installed.
To build a distribution, use `sbt stage` command. It creates `target\universal\stage`
directory in your project, which will contain all dependencies and the run script. You can
also use `sbt universal:package-bin` command if you want to compress the distribution into
a zip file.
