# sdk-dslink-scala-spark

A Scala-based DSLink implementation for Apache Spark.

## Features

- `DSAConnector` object, which can be used to establish connection with DSA
- `DSAHelper` object, which can be used to communicate with Node API via ReactiveX paradigm
- `DSAReceiver` class, which can be used to create Spark DStreams from arbitrary nodes in DSA
- DSABroker configuration via `application.conf` file (JSON/HOCON format)
- Fluent API for creating DSA nodes
- Recognizes all existing Node API data types

## Usage

### DSAHelper

Most DSAHelper methods require implicit Requester or Responder object. You can the ones provided by
DSAConnector:

```scala
implicit val requester = DSAConnector.requesterLink.getRequester
implicit val responder = DSAConnector.responderLink.getResponder
```

Example calling `DSA Invoke` method:

```scala
DSAHelper invoke (path, key1 -> value1, key2 -> value2) subscribe (
	onNext = event => println("Event received: " + event),
    onError = err => println("Error occurred: " + err),
    onCompleted = () => println("Stream closed, no more data")
)
```

Example calling `DSA Subscribe` on multiple nodes and merging the results:

```scala
val cpu = DSAHelper watch "/downstream/System/CPU_Usage"
val mem = DSAHelper watch "/downstream/System/Memory_Usage"
cpu merge mem subscribe { sv => 
	println(sv.getPath + " : " + sv.getValue)
}
``` 

Note that you can subscribe to the same path multiple times using `watch` without raising an error.

### Node Builder

This SDK also provides an extension to the Java NodeBuilder class to expose a simple DSL.

Example creating new nodes:

```scala
parentNode createChild "changeValue" display "Update Value" action (
	createAction(
		parameters = List("value" -> ValueType.NUMBER),
    	handler = result => {
      		val value = result.getParameter("value").getNumber
			DSAHelper updateNode "/output/value" -> value
		}
	)
) build
```

### Spark Streaming

To start streaming data from one or more DSA nodes, use `StreamingContext.receiverStream()` method.

```scala
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

```scala
stream foreachRDD (_ foreach { point =>
	DSAHelper.updateNode(path1, point.x)
	DSAHelper.updateNode(path2, point.y)
	...
})
```
All paths should be relative to the owner's DSLink's root node.

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
