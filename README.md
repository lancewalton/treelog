treelog
=======

[![Build Status](https://travis-ci.org/lancewalton/treelog.png?branch=master)](https://travis-ci.org/lancewalton/treelog)

It is often necessary to understand exactly what happened in a computation: what went right, what went wrong,
 what was done with what, why it was, and how a result was derived.

Such complex computations are trees, and so attempting to describe whats going on in a linear fashion is difficult to follow after the fact. 
What treelog offers is a means of writing a log together with a computation, with values computed on the way and other information relevant to
the computation.

TreeLog achieves this by making use of a Writer monad writing to a Tree representing the hierarchical log of computation.

Getting TreeLog
---------------

In SBT do this:

```scala
resolvers ++= Seq("snapshots" at "http://oss.sonatype.org/content/repositories/releases")

libraryDependencies ++= Seq(
    "com.casualmiracles" %% "treelog" % "1.0.0",
    "org.scalaz" %% "scalaz-core" % "7.0.2")
```

Quick Start TL;DR
-----------

```scala
import treelog.LogTreeSyntaxWithoutAnnotations._
import scalaz._, Scalaz._

// syntax for logging something
val one: DescribedComputation[Int] = 1 ~> "The number one"

// syntax for logging something and include the value in the log
val oneA: DescribedComputation[Int] = 1 ~> (v => s"The value is $v")

// Extract the result ( a scalaz.\/ ) and log (a LogTree which is a type alias for scalaz.Tree[LogTreeLabel[A]])
val v: \/[String, Int] = one.run.value
val logtree: LogTree = one.run.written

// turn the LogTree into a String
val logTreeString = logtree.shows

// In for comprehensions with a top level description
val result: DescribedComputation[Int] = 
 "Adding up" ~< { 
  for {
   foo <- 1 ~> ("foo = " + _)
   bar <- 2 ~> ("bar = " + _)
   foobar <- (foo + bar) ~> ("foobar = " + _)
  } yield foobar 
 }

println(result.run.written.shows)
// prints:
// Adding up
//   foo = 1
//   bar = 2
//   foobar = 3
```

Now don't be lazy and read the [scaladoc](http://lancewalton.github.io/treelog/api/master/#treelog.package).

TreeLog Examples
----------

[QuadraticRootsExample.scala](https://github.com/lancewalton/treelog/blob/master/src/test/scala/QuadraticRootsExample.scala) and
[OptionsAndEithersExample.scala](https://github.com/lancewalton/treelog/blob/master/src/test/scala/OptionsAndEithersExample.scala)
in the test package is the simplest way to see how to use TreeLog.
[FuturesExample.scala](https://github.com/lancewalton/treelog/blob/master/src/test/scala/FuturesExample.scala) demonstrates how
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

### Annotations ###

[AnnotationsExample.scala](https://github.com/lancewalton/treelog/blob/master/src/test/scala/AnnotationsExample.scala) shows how nodes in the log tree can be annotated
with important information for your program to use later. This is useful, for example, when you want to audit a process that affects multiple entities, and you
want to make sure that the audit trail is associated with each of the modified entities. You can use the annotation facility to carry the key (or something richer) for each
modified entity.
