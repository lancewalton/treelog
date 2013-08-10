treelog
=======

TreeLog enables logging as a tree structure so that comprehensive logging does not become incomprehensible.

It is often necessary to understand exactly what happened in a computation, not just what went wrong but what was actually done and with what data.
TreeLog is an attempt to add a Writer monad in the form of a Tree structure that can be used in for-comprehensions to produce a hierarchical log of a computation.

Getting TreeLog
---------------

In SBT do this:

```scala
resolvers ++= Seq("snapshots" at "http://oss.sonatype.org/content/repositories/snapshots")

libraryDependencies ++= Seq(
    "com.casualmiracles" %% "treelog" % "1.0.0-SNAPSHOT",
    "org.scalaz" %% "scalaz-core" % "7.0.2")
```

Using TreeLog - Examples
----------

### Quadratic Roots ###

[QuadraticRootsExample.scala](https://github.com/lancewalton/treelog/blob/master/src/test/scala/QuadraticRootsExample.scala) and
[OptionsAndEithersExample.scala](https://github.com/lancewalton/treelog/blob/master/src/test/scala/OptionsAndEithersExample.scala)
in the test package is the simplest way to see how to use TreeLog.

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

