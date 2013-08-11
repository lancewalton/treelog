/**
 * TreeLog enables logging as a tree structure so that comprehensive logging does not become incomprehensible.
 *
 * It is often necessary to understand exactly what happened in a computation, not just that it succeeded or failed, but what was actually done
 * and with what data.
 * TreeLog is an attempt to produce a description of the computation, which is a hierarchical log of the processing that led to the result.
 *
 * Nodes in the log tree can be annotated with important information for your program to use later. This is useful, for example, when you want to audit
 * a process that affects multiple entities, and you want to make sure that the audit trail is associated with each of the modified entities. You can use
 * the annotation facility to carry the key (or something richer) for each modified entity.
 *
 * When a computation result is 'lifted' into a [[treelog.LogTreeSyntax]].DescribedComputation by one of the many methods
 * in the [[treelog.LogTreeSyntax]] trait, it is possible to retrieve the 'value' of the computation like this:
 *
 * {{{
 * import treelog.LogTreeSyntaxWithoutAnnotation._
 * val foo = 1 ~> "Here's one"
 * val value = foo.run.value
 * // value will now be equal to scalaz.\/-(1), which represents a successful computation.
 * }}}
 *
 * The 'value' is a scalaz 'Either' (scalaz.\/). Following the usual convention:
 * <ul>
 *   <li>If it a 'left' (-\/) then the computation is a failure.</li>
 *   <li>If it is a 'right' (\/-), then the computation is a success.</li>
 * </ul>
 *
 * Likewise, it is possible to retrieve the log tree like this:
 *
 * {{{
 * import treelog.LogTreeSyntaxWithoutAnnotation._
 * val foo = 1 ~> "Here's one"
 * val logtree = foo.run.written
 * // logtree will now be an instance of LogTree which is a type alias which in this case expands to:
 * // TreeNode[LogTreeLabel[Nothing]](DescribedLogTreeLabel[Nothing]("Here's one", true, Set[Nothing]())
 * // Where:
 * //   - "Here's one" is the description provided in the declaration of foo
 * //   - true indicates that the computation represented by the node was successful
 * //   - the empty set represents the annotations specified for this node
 * }}}
 *
 * Generally, once a value has been so lifted, it is a good idea to keep working with it in that form for as long
 * as possible before accessing the <code>value</code> and <code>written</code> properties. Think monadically!
 * The two examples above show a value being lifted into the DescribedComputation. To continue to work monadically,
 * for-comprehensions come into play:
 *
 * {{{
 * import treelog.LogTreeSyntaxWithoutAnnotations._
 * import scalaz.syntax.show._
 *
 * val result = "Adding up" ~< {
 *   for {
 *    foo <- 1 ~> ("foo = " + _) // Using the overload of ~> that gives us the 'value'
 *    bar <- 2 ~> ("bar = " + _) // so that we can include it in the log messages
 *    foobar <- (foo + bar) ~> ("foobar = " + _)
 *   } yield foobar
 * }
 * println(result.run.value)
 * // Will print \/-(3) (i.e. a successful computation of 1 + 2)
 *
 * println(result.run.written.shows)
 * // Will print:
 * // Adding up
 * //   foo = 1
 * //   bar = 2
 * //   foobar = 3
 * }}}
 *
 * An extended example of this kind of thing is the
 * [[https://github.com/lancewalton/treelog/blob/master/src/test/scala/QuadraticRootsExample.scala quadratic roots example on GitHub]]
 *
 * It may seem strange that both the <code>value</code> and the log tree provide indications of success and failure (the <code>value</code>
 * through the use of <code>scalaz.\/</code>, and the log tree with a <code>boolean</code> property in the [[treelog.TreeNode]] label. The reason for this is
 * that part of a computation may fail (which we want to indicate in the log tree), but then a different strategy is tried
 * which succeeds leading to a successful overall result.
 */
package object treelog
