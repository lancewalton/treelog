/**
 * =Introduction=
 *
 * TreeLog enables logging as a tree structure so that comprehensive logging does not become incomprehensible.
 *
 * It is often necessary to understand exactly what happened in a computation, not just that it succeeded or failed, but what was actually done
 * and with what data. TreeLog is an attempt to produce a description of the computation (along with the result) which is a hierarchical log of
 * the processing that led to the result.
 *
 * Nodes in the log tree can be annotated with important information for your program to use later. This is useful, for example, when you want to audit
 * a process that affects multiple entities, and you want to make sure that the audit trail is associated with each of the modified entities. You can use
 * the annotation facility to carry the key (or something richer) for each modified entity.
 *
 * =DescribedComputation=
 *
 * All of this works by 'lifting' the intermediate and final results of computations and the description of the steps into a type called <code>DescribedComputation</code>
 * (declared in [[treelog.LogTreeSyntax]]. Although it's all very monadic, you don't have to know too much about all of that to use it.
 *
 * ==Some Simple Lifting==
 *
 * You can produce a <code>DescribedComputation</code> very simply with many of the methods in [[treelog.LogTreeSyntax]]. The simplest few are:
 *
 * {{{
 * // This is a concrete implementation of LogTreeSyntax that is provided for you
 * // to use if you don't need to use annotations (see later)
 * import treelog.LogTreeSyntaxWithoutAnnotation._
 *
 * val result1 = success(2 * 3, "Calculated product")
 * // result1 is now a DescribedComputation and carries the successful result and
 * // a single node tree telling us that produce was calculated. See below for how to
 * // extract these things.
 *
 * val result2 = failure("It's all wrong")
 * // result2 is now a DescribedComputation whose value and tree both tell us that things
 * // went wrong
 *
 * val result3 = (2 * 3) ~> "Calculated product"
 * // The same as result1
 *
 *  val result4 = (2 * 3) ~> (p => "Calculated product: " + p)
 * // The same as result1, except the description in the tree node will be "Calculated product: 6"
 * }}}
 *
 * == Extracting the Result and Log ==
 *
 * When a computation result is 'lifted' into a <code>DescribedComputation</code> by one of the many methods
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
 * It may seem strange that both the <code>value</code> and the log tree provide indications of success and failure (the <code>value</code>
 * through the use of <code>scalaz.\/</code>, and the log tree with a <code>boolean</code> property in the [[treelog.TreeNode]] label.
 * The reason for this is that part of a computation may fail (which we want to indicate in the log tree), but then a different strategy
 * is tried which succeeds leading to a successful overall result.
 *
 * == More Comprehensive Computations ==
 *
 * (An extended example of this kind of thing is the
 * [[https://github.com/lancewalton/treelog/blob/master/src/test/scala/QuadraticRootsExample.scala quadratic roots example on GitHub]])
 *
 * Generally, once a value has been lifted, it is a good idea to keep working with it in that form for as long
 * as possible before accessing the <code>value</code> and <code>written</code> properties. Think monadically!
 * The examples above show a value being lifted into the DescribedComputation. To continue to work monadically,
 * for-comprehensions come into play:
 *
 * {{{
 * import treelog.LogTreeSyntaxWithoutAnnotations._
 * import scalaz.syntax.show._
 *
 * val result = for {
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
 * // No description
 * //   foo = 1
 * //   bar = 2
 * //   foobar = 3
 * }}}
 *
 * (For those struggling with the full power of for-comprehensions, I suggest turning the above example into its unsugared flatmap/map form
 * to see what is going on. The central point is that <code>foo</code> will have the value 1, <code>bar</code> will have the value 2, and
 * <code>foobar</code> will have the value 3; the monadic stuff all happens in the background.)
 *
 * == Describing Branches ==
 *
 * The log tree above has a root node with 'No description' and three child (leaf) nodes with descriptions obviously obtained from the
 * arguments to the right of the <code>~&gt;</code> operators in the for-comprehension. Producing a hierarchical log isn't much use if we
 * can't describe the non-leaf elements. We can do that in two ways (this looks ugly, but read on&#8230;):
 *
 * {{{
 * import treelog.LogTreeSyntaxWithoutAnnotations._
 * import scalaz.syntax.show._
 *
 * val result = for {
 *    foo <- 1 ~> ("foo = " + _) // Using the overload of ~> that gives us the 'value'
 *    bar <- 2 ~> ("bar = " + _) // so that we can include it in the log messages
 *    foobar <- (foo + bar) ~> ("foobar = " + _)
 *   } yield foobar
 * }
 *
 * val resultWithDescription1 = result ~> "Adding up"
 * println(resultWithDescription1.run.written.shows)
 * // Will print:
 * // Adding up
 * //   foo = 1
 * //   bar = 2
 * //   foobar = 3
 *
 * val resultWithDescription2 = "Adding up" ~< result
 * println(resultWithDescription2.run.written.shows)
 * // Will also print:
 * // Adding up
 * //   foo = 1
 * //   bar = 2
 * //   foobar = 3
 * }}}
 *
 * The first approach (<code>resultWithDescription1</code> using <code>~gt;</code>) will generally be used when a method/function used to provide an intermediate result
 * in the middle of a for-comprehension returns an undescribed root node. Then the code flows quite nicely thus:
 *
 * {{{
 * val result = for {
 *    something <- doSomething() ~> "Something has been done"
 *    more <- doMore(soemthing) ~> "More has been done"
 *   } yield more
 * }}}
 *
 * Here, <code>doSomething()</code> and <code>doMore(...)</code> return DescribedComputations carrying a log tree with an undescribed root node.
 * They have been given descriptions in the above for-comprehension.
 *
 * The second approach (<code>resultWithDescription2</code> using <code>~&lt;</code>) will generally be used when a for-comprehension yields a
 * <code>DescribedComputation</code> (which will always have a log tree with an undescribed root node if the for-comprehension has more than
 * one variable), and you want to immediately give the root node a description. In this case, it is more natural to write:
 *
 * {{{
 * val result = "Adding up" ~< {
 *   for {
 *     foo <- 1 ~> ("foo = " + _)
 *     bar <- 2 ~> ("bar = " + _)
 *     foobar <- (foo + bar) ~> ("foobar = " + _)
 *   } yield foobar
 * }
 * }}}
 *
 * Both of these approaches are demonstrated in the
 * [[https://github.com/lancewalton/treelog/blob/master/src/test/scala/QuadraticRootsExample.scala quadratic roots example]]. There is no good
 * reason for mixing the two approaches in that example, other than for purposes of demonstration.
 */
package object treelog
