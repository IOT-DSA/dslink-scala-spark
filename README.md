# sdk-dslink-scala-spark

![alt tag](https://travis-ci.org/IOT-DSA/sdk-dslink-scala-spark.svg?branch=master)
![Coverage Status](https://coveralls.io/repos/github/IOT-DSA/sdk-dslink-scala-spark/badge.svg)

A Scala-based DSLink implementation for Apache Spark.

## Features

- DSAReceiver class for communicating with DSA broker.

## Usage

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
