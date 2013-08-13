/**
 * =Introduction=
 *
 * TreeLog enables logging as a tree structure so that comprehensive logging does not become incomprehensible.
 *
 * It is often necessary to understand exactly what happened in a computation, not just that it succeeded or failed, but what was actually done
 * and with what data. TreeLog is an attempt to produce a description of the computation (along with the result) which is a hierarchical log of
 * the processing that led to the result. Note that in the remainder of this document, results of producing log trees will be shown by rendering
 * the tree textually, but that is only one possible use. Another example of use would be to store the tree in a database so that users can see
 * a detailed audit trail of the processing that has occurred for particular entities.
 *
 * Nodes in the log tree can be annotated with important information for your program to use later. This is useful, for example, when you want to audit
 * a process that affects multiple entities, and you want to make sure that the audit trail is associated with each of the modified entities. You can use
 * the annotation facility to carry the key (or something richer) for each modified entity.
 *
 * =DescribedComputation=
 *
 * All of this works by 'lifting' the intermediate and final results of computations and the description of the steps into a type called <code>DescribedComputation</code>
 * (declared in [[treelog.LogTreeSyntax LogTreeSyntax]]. Although it's all very monadic, you don't have to know too much about all of that to use it.
 *
 * ==Some Simple Lifting==
 *
 * You can produce a <code>DescribedComputation</code> very simply with many of the methods in [[treelog.LogTreeSyntax LogTreeSyntax]]. The simplest few are:
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
 * <code>result3</code> and <code>result4</code> above introduce the first pieces of syntax related to producing
 * <code>DescribedComputations</code>. In this case it lifts the value into the <code>DescribedComputation</code>
 * and creates a leaf node in the associated log tree. See [[treelog.LogTreeSyntax.LeafSyntax LeafSyntax]] for
 * related simple syntax for leaves.
 *
 * == Extracting the Result and Log ==
 *
 * When a computation result is 'lifted' into a <code>DescribedComputation</code> by one of the many methods
 * in the [[treelog.LogTreeSyntax LogTreeSyntax]] trait, it is possible to retrieve the 'value' of the computation like this:
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
 * through the use of <code>scalaz.\/</code>, and the log tree with a <code>boolean</code> property in the [[treelog.TreeNode TreeNode]] label.
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
 * == Non-Leaf Nodes ==
 *
 * Non-leaf nodes (branches) are created explicitly by the developer or implicity by the [[logtree.LogTreeSyntax LogTreeSyntax]] under
 * various conditions.
 *
 * The log tree above has a root node with 'No description' and three child (leaf) nodes with descriptions obviously obtained from the
 * arguments to the right of the <code>~&gt;</code> operators in the for-comprehension. This is because the three leaf nodes explicitly
 * created in that for-comprehension need to be placed somewhere while the log tree is produced. An obvious thing to do was to make them
 * child nodes of a branch, which [[logtree.LogTreeSyntax LogTreeSyntax]] does, using some rules for when to create a new branch to
 * contain existing children and when to just add new children to an existing branch.
 *
 * However, at the time the branch is created there is no ready description available for it, hence the "No description" text when the tree is
 * shown using the <code>scalaz.Show</code> defined for it. Producing a hierarchical log isn't much use if we
 * can't describe the non-leaf elements. We can provide a description in two ways (this looks ugly, but read and it will get more elegant&#8230;):
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
 * one generator), and you want to immediately give the root node a description. In this case, it is more natural to write:
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
 *
 * <code>~&lt;</code> works not only for <code>DescribedComputation</code>, but for any <code>F[DescribedComputation]</code> as long as
 * <code>F</code> has a <code>scalaz.Monad</code> and a <code>scalaz.Traverse</code> defined and available in implicit scope.
 * See [[treelog.LogTreeSyntax.BranchLabelingSyntax BranchLabelingSyntax]].
 *
 * = Special Lifting =
 *
 * == Boolean, Option and \/ ==
 *
 * <code>Boolean</code>, <code>Option</code> and <code>\/</code> (scalaz's 'Either'), have some special syntax <code>~&gt;?</code> associated with
 * them to allow <code>true</code>, <code>Some(.)</code> and <code>\/-(.)</code> to be treated as successful computational outcomes, and
 * <code>false</code>, <code>None</code> and <code>-\/(.)</code> to be treated as failure conditions. For example:
 *
 * {{{
 * val result = false ~>? "Doing a thing with a Boolean"
 * println(result.run.value)
 * // Will print -\/("Doing a thing with a Boolean") (note that it's a 'left')
 *
 * println(result.run.written.shows)
 * // Will print:
 * // Failed: Doing a thing with a Boolean
 * }}}
 *
 * Notice how the <code>Show</code> for the [[treelog.Tree Tree]] indicates that the result is a failure. This failure information exists in the
 * [[treelog.TreeNode TreeNode's]] <code>label</code> property (which is generic in [[treelog.TreeNode TreeNode]] and instantiated as a
 * [[treelog.LogTreeLabel LogTreeLabel]] in the [[treelog.LogTreeSyntax LogTreeSyntax]] methods.
 *
 * <code>~&gt;?</code> is overloaded for each of the three types above to allow either a simple description to be given (as in the example above)
 * or for different descriptions to be given in the success versus failure case. Also, in the case of <code>Option</code> and <code>\/</code>,
 * overloads are provided to pass the values contained. See [[treelog.LogTreeSyntax.BooleanSyntax BooleanSyntax]],
 * [[treelog.LogTreeSyntax.OptionSyntax OptionSyntax]], [[treelog.LogTreeSyntax.EitherSyntax EitherSyntax]].
 *
 * Note that it is easy to get drawn into always using this syntax for these three types. But sometimes, for example, a <code>Boolean</code>
 * <code>false</code> does not indicate a failure in a computation and so <code>~&gt;?</code> is not appropriate. Keep in mind that 'failure'
 * means that the computation will stop, whereas 'success' will mean that the computation will continue.
 *
 * == Traversable Monads ==
 *
 * Suppose you have a <code>List[A]</code> and a function <code>f: A => DescribedComputation[B]</code>, and you want to apply <code>f(.)</code>
 * to each element of the list to produce <code>DescribedComputations</code> for each element. That's easy enough. But suppose you now want
 * to take all of the 'values' (<code>vs</code>) contained in the list of <code>DescribedComputations</code> thus produced, and create a new
 * <code>DescribedComputation</code> whose value is <code>vs</code> and whose log tree is a branch with a description and whose children
 * are the log trees resulting from each application of <code>f(.)</code>.
 *
 * We needed to do precisely that very often, so we wrote some syntax for it:
 *
 * {{{
 * val result = List(1, 2, 3) ~>* ("Double the values", x => (x * 2) ~> (y => s"Double $x = $y"))
 *
 * println(result.run.value)
 * // Will print \/-(List(2, 4, 6))
 *
 * println(result.run.written.shows)
 * // Will print:
 * // Double the values
 * //   Double 1 = 2
 * //   Double 2 = 4
 * //   Double 3 = 6
 * }}}
 *
 * This is particularly useful if there is a possibility that <code>f(.)</code> can produce <code>DescribedComputations</code> that represent failures,
 * because hoisting children into a branch of a log tree gives the branch a successful status only if all of the children are successful (this is true
 * of all syntax that does this). Hence:
 *
 * {{{
 * val result = List(1, 2, 3) ~>* ("All even", x => (x % 2 == 0) ~>? s"Testing if $x is even"))
 *
 * println(result.run.value)
 * // Will print -\/("All even") - Notice that it's a 'left', meaning failure
 *
 * println(result.run.written.shows)
 * // Will print:
 * // Failed: All even
 * //   Failed: Testing if 1 is even
 * //   Testing if 2 is even
 * //   Failed: Testing if 3 is even
 * }}}
 *
 * <code>~&gt;*</code> works not only for <code>List</code>, but for all kinds that have a <code>scalaz.Monad</code> and a <code>scalaz.Traverse</code>
 * defined and available in implicit scope. See [[treelog.LogTreeSyntax.TraversableMonadSyntax TraversableMonadSyntax]].
 *
 * == Annotations ==
 *
 * Nodes in the log tree can be annotated with important information for your program to use later. This is useful, for example, when you want to audit
 * a process that affects multiple entities, and you want to make sure that the audit trail is associated with each of the modified entities. You can use
 * the annotation facility to carry the key (or something richer) for each modified entity.
 *
 * The <code>~~</code> (see [[treelog.LogTreeSyntax.AnnotationsSyntax AnnotationsSyntax]]) is provided for this purpose. It can be applied to
 * any <code>DescribedComputation</code> and it will add the given annotation to the set of annotations at the current root node of the log
 * tree. Annotations can be of any type, but must all be of the same type for a particular log tree. You choose the type of annotations by instantiating
 * the 'Annotation' type parameter of [[treelog.LogTreeSyntax LogTreeSyntax]]
 *
 * Here is a simple example using <code>Strings</code> as the annotations type:
 *
 * {{{
 * val stringAnnotateableLogTreeSyntax = new treelog.LogTreeSyntax[String] {}
 * import stringAnnotateableLogTreeSyntax._
 *
 * val result = 1 <- "This is the description" ~~ "This is the annotation"
 *
 * println(result.run.value)
 * // Will print \/-(1)
 *
 * println(result.run.written.shows)
 * // Will print:
 * // This is the description - [This is the annotation]
 * }}}
 *
 * See the [[https://github.com/lancewalton/treelog/blob/master/src/test/scala/AnnotationsExample.scala annotations example]]
 * for a more comprehensive example.
 */
package object treelog
