treelog
=======

TreeLog enables logging as a tree structure so that comprehensive logging does not become incomprehensible.

It is often necessary to understand exactly what happened in a computation, not just what went wrong but what was actually done and with what data.
TreeLog is an attempt to add a Writer monad in the form of a Tree structure that can be used in for-comprehensions to produce a heirarchical log of a computation.

Getting TreeLog
---------------

In SBT do this:

```scala
resolvers ++= Seq("snapshots" at "http://oss.sonatype.org/content/repositories/snapshots")

libraryDependencies ++= Seq(
    "com.casualmiracles" %% "treelog" % "1.0.0-SNAPSHOT",
    "org.scalaz" %% "scalaz-core" % "7.0.2")
```

Using TreeLog by an Example
----------
[LogTreeExample.scala](https://github.com/lancewalton/treelog/blob/master/src/test/scala/LogTreeExample.scala) in the test package in the simplest way to see how to use TreeLog.

The example does the extremely important of logging the computation of roots of a quadratic equation.

In the example, this:

```scala
root(Parameters(2, 5, 3)).run.written.shows
```

results in 

<pre>   
Extracting root
  Calculating Numerator
    Calculating Determinant
      Calculating b^2
        Got b: 5.0
        Got b^2: 25.0
      Calculating 4ac
        Got a: 2.0
        Got c: 3.0
        Got 4ac: 24.0
      Got b^2 - 4ac: 1.0
    Calculating sqrt(determinant)
      Determinant (1.0) is >= 0
      Got sqrt(determinant): 1.0
    Got b: 5.0
    Got -b: -5.0
    Got -b + sqrt(determinant): -4.0
  Calculating Denominator
    Got a: 2.0
    Got 2a: 4.0
  Got root = numerator / denominator: -1.0
</pre>

Or, in the case of a failure (no complex roots)

```scala
root(Parameters(2, 5, 10)).run.written.shows
```

gives

<pre>    
Extracting root: Failed
  Calculating Numerator: Failed
    Calculating Determinant
      Calculating b^2
        Got b: 5.0
        Got b^2: 25.0
      Calculating 4ac
        Got a: 2.0
        Got c: 10.0
        Got 4ac: 80.0
      Got b^2 - 4ac: -55.0
    Calculating sqrt(determinant): Failed
      Determinant (-55.0) is < 0: Failed

</pre>
