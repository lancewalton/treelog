# treelog

[![Scala CI](https://github.com/lancewalton/treelog/actions/workflows/scala.yml/badge.svg)](https://github.com/lancewalton/treelog/actions/workflows/scala.yml)

It is often necessary to understand exactly what happened in a computation: what went right, what went wrong,
 what was done with what, why it was done, and how a result was derived.

Such complex computations are trees, and so attempting to describe what's going on in a linear fashion is difficult to follow after the fact. 
What TreeLog offers is a means of writing a log together with a computation, with values computed on the way with all information relevant to
the computation. 

TreeLog is a veritable complect of computation and description in perfect harmony.

TreeLog achieves this remarkable feat with a Writer monad writing to a Tree representing the hierarchical log of computation.

## Getting TreeLog

Scala 2.13.x, 3.3.x, 3.4.x, 3.5.x, 3.6.x:

```scala
libraryDependencies ++= Seq("com.casualmiracles" %% "treelog-cats" % "1.9.2")
```

## TreeLog Examples

[QuadraticRootsExample.scala](https://github.com/lancewalton/treelog/blob/main/src/test/scala/QuadraticRootsExample.scala) and
[OptionsAndEithersExample.scala](https://github.com/lancewalton/treelog/blob/main/src/test/scala/OptionsAndEithersExample.scala)
in the test package is the simplest way to see how to use TreeLog.
[FuturesExample.scala](https://github.com/lancewalton/treelog/blob/main/src/test/scala/FuturesExample.scala) demonstrates how
you can combine the results of several parallel computations.

The quadratic example does the extremely important job of logging the computation of roots of a quadratic equation. This:

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

Or, in the case of a failure (when the roots are complex)

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
      Determinant (-55.0) is &lt; 0: Failed
</pre>


## Older Releases

For Scala 2.12.x and scalaz 7.2.x:

```scala
libraryDependencies ++= Seq(
    "com.casualmiracles" %% "treelog-scalaz-72x" % "1.4.3",
    "org.scalaz" %% "scalaz-core" % "7.2.8")
```

For Scala 2.12.x and 2.11.x and scalaz 7.3.x:

```scala
libraryDependencies ++= Seq(
    "com.casualmiracles" %% "treelog" % "1.4.10",
    "org.scalaz" %% "scalaz-core" % "7.3.0-M18")
```

For Scala 2.12.x and cats 1.6.0:

```scala
libraryDependencies ++= Seq("com.casualmiracles" %% "treelog-cats" % "1.4.9")
```

For Scala 2.12.x and scalaz 7.1.x:

```scala
libraryDependencies ++= Seq(
    "com.casualmiracles" %% "treelog-scalaz-71x" % "1.4.0",
    "org.scalaz" %% "scalaz-core" % "7.1.11")
```

For Scala 2.11.x and scalaz 7.2.x:

```scala
libraryDependencies ++= Seq(
    "com.casualmiracles" %% "treelog" % "1.3.0",
    "org.scalaz" %% "scalaz-core" % "7.2.0")
```


For Scala 2.11.x and scalaz 7.1.x:

```scala
libraryDependencies ++= Seq(
    "com.casualmiracles" %% "treelog" % "1.2.6",
    "org.scalaz" %% "scalaz-core" % "7.1.7")
```

For Scala 2.10.x

```scala
libraryDependencies ++= Seq(
    "com.casualmiracles" %% "treelog" % "1.2.2",
    "org.scalaz" %% "scalaz-core" % "7.0.6")
```
