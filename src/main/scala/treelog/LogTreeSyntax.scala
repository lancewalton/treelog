package treelog

import scalaz.{ -\/, \/-, \/, EitherT, Functor, Traverse, Monad, Monoid, Show, Writer, Tree, idInstance }
import scalaz.syntax.foldable
import scalaz.syntax.traverse._
import scalaz.syntax.monadListen._
import scala.annotation.tailrec

/**
 * See the [[treelog]] package documentation for a brief introduction to treelog and also,
 * [[https://github.com/lancewalton/treelog#using-treelog---examples examples on GitHub]] to get started.
 *
 * This trait provides syntax for manipulating <code>DescribedComputations</code>. Either:
 * <ul>
 *   <li>extend this trait, or</li>
 *   <li>define an object with the appropriate Annotation type and import on demand</li>
 * </ul>
 */
trait LogTreeSyntax[Annotation] {
  type LogTree = Tree[LogTreeLabel[Annotation]]
  type LogTreeWriter[+Value] = Writer[LogTree, Value]
  type DescribedComputation[+Value] = EitherT[LogTreeWriter, String, Value]

  private val NilTree: LogTree = Tree(UndescribedLogTreeLabel(true))

  implicit val logTreeMonoid = new Monoid[LogTree] {
    val zero = NilTree

    def append(augend: LogTree, addend: ⇒ LogTree): LogTree =
      (augend, addend) match {
        case (NilTree, r) ⇒ r

        case (l, NilTree) ⇒ l

        case (Tree.Node(leftLabel: UndescribedLogTreeLabel[Annotation], leftChildren), Tree.Node(rightLabel: UndescribedLogTreeLabel[Annotation], rightChildren)) ⇒
          Tree.node(UndescribedLogTreeLabel(leftLabel.success && rightLabel.success, leftLabel.annotations ++ rightLabel.annotations), leftChildren ++ rightChildren)

        case (Tree.Node(leftLabel: UndescribedLogTreeLabel[Annotation], leftChildren), rightNode @ Tree.Node(rightLabel, rightChildren)) ⇒
          Tree.node(UndescribedLogTreeLabel(leftLabel.success && rightLabel.success), leftChildren :+ rightNode)

        case (leftNode @ Tree.Node(leftLabel, leftChildren), Tree.Node(rightLabel: UndescribedLogTreeLabel[Annotation], rightChildren)) ⇒
          Tree.node(UndescribedLogTreeLabel(leftLabel.success && rightLabel.success), leftNode #:: rightChildren)

        case (leftNode: Tree[LogTreeLabel[Annotation]], rightNode: Tree[LogTreeLabel[Annotation]]) ⇒
          Tree.node(UndescribedLogTreeLabel(leftNode.rootLabel.success && rightNode.rootLabel.success), Stream(leftNode, rightNode))
      }
  }

  private implicit val eitherWriter = EitherT.monadListen[Writer, LogTree, String]

  private def failure[Value](description: String, tree: LogTree): DescribedComputation[Value] =
    for {
      _ ← eitherWriter.tell(tree)
      err ← eitherWriter.left(description)
    } yield err

  private def success[Value](value: Value, tree: LogTree): DescribedComputation[Value] = eitherWriter.right(value) :++>> (_ ⇒ tree)

  def failureLog[Value](dc: DescribedComputation[Value]): DescribedComputation[Value] = {
    val logTree = dc.run.written match {
      case Tree.Node(UndescribedLogTreeLabel(s, a), c)  ⇒ Tree.node(UndescribedLogTreeLabel(false, a), c)
      case Tree.Node(DescribedLogTreeLabel(d, s, a), c) ⇒ Tree.node(DescribedLogTreeLabel(d, false, a), c)
    }
    dc.run.value match {
      case -\/(des) ⇒ failure(des, logTree)
      case \/-(a)   ⇒ success(a, logTree)
    }
  }

  /**
   * Create a <code>DescribedComputation</code> representing a failure using the given <code>description</code> for both the log tree label and as
   * the content of the <code>value</code>, which will be a [[scalaz.-\/]].
   */
  def failure[Value](description: String): DescribedComputation[Value] = failure(description, Tree.leaf(DescribedLogTreeLabel(description, false)))

  /**
   * Create a <code>DescribedComputation</code> representing a success with the given <code>value</code> (lifted into a [[scalaz.\/-]]) and the given
   * <code>description</code> in the log tree.
   */
  def success[Value](value: Value, description: String): DescribedComputation[Value] =
    eitherWriter.right(value) :++>> (_ ⇒ Tree.leaf(DescribedLogTreeLabel(description, true, Set[Annotation]())))

  /**
   * Syntax for lifting values into <code>DescribedComputations</code> and creating leaf nodes in the log tree.
   */
  implicit class LeafSyntax[Value](value: Value) {
    /**
     * Create a 'success' <code>DescribedComputation</code> with <code>\/-(value)</code> as the value and
     * a success [[treelog.TreeNode TreeNode]] with the given <code>description</code>.
     *
     * {{{
     * import treelog.LogTreeSyntaxWithoutAnnotations._
     * import scalaz.syntax.show._
     *
     * val leaf = 1 ~> "One"
     * println(result.run.value)
     * // Will print: \/-(1) - note that the 'right' means 'success'
     *
     * println(result.run.written.shows)
     * // Will print:
     * // One
     * }}}
     */
    def ~>(description: String): DescribedComputation[Value] = success(value, description)

    /**
     * Create a 'success' <code>DescribedComputation</code> with <code>\/-(value)</code> as the value and
     * a success [[treelog.TreeNode TreeNode]] using the given <code>description</code> function to generate
     * a description for the tree node's label.
     *
     * {{{
     * import treelog.LogTreeSyntaxWithoutAnnotations._
     * import scalaz.syntax.show._
     *
     * val leaf = 1 ~> (x => s"One: $x")
     * println(result.run.value)
     * // Will print: \/-(1) - note that the 'right' means 'success'
     *
     * println(result.run.written.shows)
     * // Will print:
     * // One: 1
     * }}}
     */
    def ~>(description: Value ⇒ String): DescribedComputation[Value] = ~>(description(value))

    /**
     * Create a 'failure' <code>DescribedComputation</code> with <code>-\/(description)</code> as the value and
     * a failure [[treelog.TreeNode TreeNode]] with the given <code>description</code>.
     *
     * {{{
     * import treelog.LogTreeSyntaxWithoutAnnotations._
     * import scalaz.syntax.show._
     *
     * val leaf = 1 ~>! "One"
     * println(result.run.value)
     * // Will print: -\/("One") - note that the 'left' means 'failure', and the contained value is the description, not the 1.
     *
     * println(result.run.written.shows)
     * // Will print:
     * // Failed: One
     * }}}
     */
    def ~>!(description: String): DescribedComputation[Value] = failure(description)

    /**
     * Create a 'failure' <code>DescribedComputation</code> using the given <code>description</code> function to
     * generate a description for the tree node's label and for the <code>DescribedComputations</code> value (i.e.
     * the value will be <code>\/-(description(value))</code>.
     *
     * {{{
     * import treelog.LogTreeSyntaxWithoutAnnotations._
     * import scalaz.syntax.show._
     *
     * val leaf = 1 ~>! (x => s"One - $x")
     * println(result.run.value)
     * // Will print: -\/("One") - note that the 'left' means 'failure', and the contained value is the description, not the 1.
     *
     * println(result.run.written.shows)
     * // Will print:
     * // Failed: One - 1
     * }}}
     */
    def ~>!(description: Value ⇒ String): DescribedComputation[Value] = ~>!(description(value))
  }

  /**
   * Syntax for allowing annotations to be added to log tree nodes.
   *
   * The best way to see how this syntax works is to take a look at the
   * [[https://github.com/lancewalton/treelog#annotations annotations example]] on GitHub.
   *
   * Here is a short example:
   *
   * {{{
   * import scalaz.syntax.show._
   *
   * val syntax = new LogTreeSyntax[String] {}
   * import syntax._
   *
   * val result = 1 ~> "One" ~~ Set("Annotating with a string", "And another")
   * println(result.run.value)
   * // Will print: \/-(1) - note that the 'right' means 'success'
   *
   * println(result.run.written.shows)
   * // Will print:
   * // One - [Annotating with a string, And another]
   * }}}
   */
  implicit class AnnotationsSyntax[Value](w: DescribedComputation[Value]) {
    def ~~(annotations: Set[Annotation]): DescribedComputation[Value] = {
      val newTree = w.run.written match {
        case Tree.Node(l: DescribedLogTreeLabel[Annotation], c)   ⇒ Tree.node(l.copy(annotations = l.annotations ++ annotations), c)
        case Tree.Node(l: UndescribedLogTreeLabel[Annotation], c) ⇒ Tree.node(l.copy(annotations = l.annotations ++ annotations), c)
      }

      w.run.value match {
        case -\/(error) ⇒ failure(error, newTree)
        case \/-(value) ⇒ success(value, newTree)
      }
    }

    /**
     * Syntactic sugar equivalent to <code>~~ Set(annotation)</code>
     */
    def ~~(annotation: Annotation): DescribedComputation[Value] = ~~(Set(annotation))

    /**
     * Equivalent to <code>~~ annotations</code>
     */
    def annotateWith(annotations: Set[Annotation]): DescribedComputation[Value] = ~~(annotations)

    /**
     * Equivalent to <code>~~ annotation</code>
     */
    def annotateWith(annotation: Annotation): DescribedComputation[Value] = ~~(annotation)

    /**
     * Get the union of all annotations in the log tree of the DescribedComputation
     */
    def allAnnotations: Set[Annotation] = {
      def recurse(tree: LogTree, accumulator: Set[Annotation]): Set[Annotation] = tree.subForest.foldLeft(accumulator ++ tree.rootLabel.annotations)((acc, child) ⇒ recurse(child, acc))
      recurse(w.run.written, Set())
    }
  }

  /**
   * Syntax for treating booleans as signifiers of success or failure in a computation.
   *
   * The simplest usage is something like: <code>myBoolean ~>? "Is my boolean true?"</code>. The 'value'
   * and log tree of the returned <code>DescribedComputation</code> will indicate success or failure
   * depending on the value of <code>myBoolean</code>.
   */
  implicit class BooleanSyntax(b: Boolean) {
    /**
     * Use the same description whether the boolean is <code>true</code> or <code>false</code>.
     * Equivalent to <code>~>?(description, description)</code>
     */
    def ~>?(description: String): DescribedComputation[Boolean] =
      ~>?(description, description)

    /**
     * Use different descriptions for the <code>true</code> and <code>false</code> cases. Note that unlike <code>'if'</code>
     * the <code>false</code> / failure description is the first parameter and the <code>true</code> / success
     * description is the second parameter. This is to maintain consistency with [[treelog.LogTreeSyntax.OptionSyntax OptionSyntax]]
     * and [[treelog.LogTreeSyntax.EitherSyntax EitherSyntax]].
     *
     * If the boolean is <code>true</code> the 'value' of the returned DescribedComputation will be <code>\/-(true)</code>,
     * otherwise, the 'value' will be <code>-\/(description)</code>.
     */
    def ~>?(failureDescription: ⇒ String, successDescription: ⇒ String): DescribedComputation[Boolean] =
      if (b) success(true, successDescription) else failure(failureDescription)
  }

  /**
   * Syntax for treating <code>Options</code> as signifiers of success or failure in a computation.
   *
   * The simplest usage is something like: <code>myOption ~>? "Do I have Some?"</code>. The 'value'
   * and log tree of the returned <code>DescribedComputation</code> will indicate success or failure
   * depending on the value of <code>myOption</code>.
   */
  implicit class OptionSyntax[Value](option: Option[Value]) {
    /**
     * Use the same description whether the Option is <code>Some</code> or <code>None</code>.
     * Equivalent to <code>~>?(description, description)</code>
     */
    def ~>?(description: String): DescribedComputation[Value] = ~>?(description, description)

    /**
     * Use different descriptions for the <code>Some</code> and <code>None</code> cases.
     *
     * If the option is <code>Some(x)</code> the 'value' of the returned DescribedComputation will be <code>\/-(x)</code>,
     * otherwise, the 'value' will be <code>-\/(noneDescription)</code>.
     */
    def ~>?(noneDescription: ⇒ String, someDescription: ⇒ String): DescribedComputation[Value] = ~>?(noneDescription, _ ⇒ someDescription)

    /**
     * Use different descriptions for the <code>Some</code> and <code>None</code> cases, providing the boxed <code>Some</code>
     * value to the function used to produce the description for the <code>Some</code> case, so that it can be included in the
     * description if you wish.
     *
     * If the option is <code>Some(x)</code> the 'value' of the returned DescribedComputation will be <code>\/-(x)</code>,
     * otherwise, the 'value' will be <code>-\/(noneDescription)</code>.
     */
    def ~>?(noneDescription: ⇒ String, someDescription: Value ⇒ String): DescribedComputation[Value] =
      option map { a ⇒ success(a, someDescription(a)) } getOrElse failure(noneDescription)

    /**
     * Return a default <code>DescribedComputation</code> if <code>option</code> is a <code>None</code>.
     *
     * If the option is <code>Some(x)</code> the 'value' of the returned DescribedComputation will be <code>\/-(Some(x))</code>,
     * otherwise, the returned <code>DescribedComputation</code> will be <code>dflt</code>.
     */
    def ~>|[B](f: Value ⇒ DescribedComputation[B], dflt: ⇒ DescribedComputation[Option[B]]): DescribedComputation[Option[B]] =
      option.map(f).map((v: DescribedComputation[B]) ⇒ v.map(w ⇒ Some(w))) getOrElse dflt
  }

  /**
   * Syntax for treating <code>scalaz.\/</code> as signifiers of success or failure in a computation.
   *
   * The simplest usage is something like: <code>myEither ~>? "Do I have the right?"</code>. The 'value'
   * and log tree of the returned <code>DescribedComputation</code> will indicate success or failure
   * depending on the value of <code>myEither</code>.
   */
  implicit class EitherSyntax[Value](either: \/[String, Value]) {
    /**
     * Use different descriptions depending on whether <code>either</code> is a <code>\/-</code> or a <code>-\/</code>.
     */
    def ~>?(leftDescription: String ⇒ String, rightDescription: ⇒ String): DescribedComputation[Value] =
      ~>?(leftDescription, _ ⇒ rightDescription)

    /**
     * Use the same description regardless of whether <code>either</code> is a <code>\/-</code> or a <code>-\/</code>.
     * Equivalent to: <code>~>?((error: String) ⇒ s"$description - $error", description)</code>
     */
    def ~>?(description: String): DescribedComputation[Value] =
      ~>?((error: String) ⇒ s"$description - $error", description)

    /**
     * Use the given description if <code>either</code> is a <code>\/-</code>. If <code>either</code> is
     * <code>-\/(message)</code>, use <code>message</code> as the description.
     */
    def ~>?(description: Value ⇒ String): DescribedComputation[Value] =
      ~>?((error: String) ⇒ error, description)

    /**
     * Use the given functions to provide descriptions depending on whether <code>either</code> is a
     * <code>\/-</code> or <code>-\/</code>
     */
    def ~>?(leftDescription: String ⇒ String, rightDescription: Value ⇒ String): DescribedComputation[Value] =
      either.fold(error ⇒ failure(leftDescription(error)), a ⇒ success(a, rightDescription(a)))
  }

  /**
   * Syntax for labeling or creating new branches in a log tree given a description.
   */
  implicit class BranchLabelingSyntax(description: String) {
    /**
     * Create a new branch given a monadic, traversable 'container' <code>F[DescribedComputation[Value]]</code>, 'sequence' it
     * to create a <code>DescribedComputation[F[Value]]</code>, and give the new <code>DescribedComputation's</code> log tree a
     * new root node, with the given <code>description</code> and whose children are the trees in the
     * <code>F[DescribedComputation[Value]]</code>.
     *
     * For example, if we evaluate this method with <code>F</code> instantiated as <code>List</code>, we would turn a
     * <code>List[DescribedComputation[Value]]</code> into a <code>DescribedComputation[List[Value]]</code>, such that the
     * <code>List[Value]</code> which is the result's 'value' is obtained by extracting the 'value' from each
     * <code>DescribedComputation</code> in the <code>describedComputations</code> parameter. Likewise, the child nodes
     * of the returned log tree root node are obtained by extracting the log tree from each of the <code>describedComputations</code>.
     *
     * The 'success' status of the returned <code>DescribedComputations</code> log tree is <code>true</code> if all of the children
     * are successful. It is <code>false</code> otherwise.
     */
    def ~<[F[_], Value](describedComputations: F[DescribedComputation[Value]])(implicit monad: Monad[F], traverse: Traverse[F]): DescribedComputation[F[Value]] =
      ~<+(describedComputations, (x: F[Value]) ⇒ x)

    /**
     * As ~< but folding over the resulting F[Value] to yield R and return a DescribedComputation[R] with all the logs.
     *
     * For example, given l = List[DescribedComputation[Int]], and f = List[Int] => Int (say summing the list), then
     * <code>"Sum" ~&lt;+(l, f)</code> would return a DescribedComputation containing the sum of the elements of the list.
     */
    def ~<+[F[_], Value, R](describedComputations: F[DescribedComputation[Value]], f: F[Value] ⇒ R)(implicit monad: Monad[F], traverse: Traverse[F]): DescribedComputation[R] = {
      val parts = monad.map(describedComputations)(m ⇒ (m.run.value, m.run.written))

      val children = monad.map(parts)(_._2).toList
      val branch = Tree.node(
        DescribedLogTreeLabel(
          description,
          allSuccessful(children),
          Set[Annotation]()),
        children.toStream)

      describedComputations.sequence.run.value match {
        case -\/(_) ⇒ failure(description, branch)
        case \/-(v) ⇒ success(f(v), branch)
      }
    }

    /**
     * If <code>dc</code> has a log tree with an undescribed root node, give the root node the <code>description</code> but otherwise
     * leave it unchanged. If the log tree has a described root node, create a new root node above the existing one and give the
     * new root node the <code>description</code>. In both cases preserve the <code>value</code> and success/failure status.
     */
    def ~<[Value](dc: DescribedComputation[Value]): DescribedComputation[Value] =
      dc.run.value match {
        case -\/(_)     ⇒ failure(description, branchHoister(dc.run.written, description))
        case \/-(value) ⇒ success(value, branchHoister(dc.run.written, description))
      }

    private def branchHoister(tree: LogTree, description: String): LogTree = tree match {
      case Tree.Node(l: UndescribedLogTreeLabel[Annotation], children) ⇒ Tree.node(DescribedLogTreeLabel(description, allSuccessful(children)), children)
      case Tree.Node(l: DescribedLogTreeLabel[Annotation], children)   ⇒ Tree.node(DescribedLogTreeLabel(description, allSuccessful(List(tree))), Stream(tree))
    }

    private def allSuccessful(trees: Iterable[LogTree]) = trees.forall(_.rootLabel.success)
  }

  /**
   * Syntax for dealing with traversable monads
   */
  implicit class TraversableMonadSyntax[F[_], Value](values: F[Value])(implicit monad: Monad[F], traverse: Traverse[F]) {
    /**
     * This method is syntactic sugar for <code>description ~< monad.map(values)(f)</code>
     */
    def ~>*[B](description: String, f: Value ⇒ DescribedComputation[B]): DescribedComputation[F[B]] = description ~< monad.map(values)(f)
  }

  /**
   * Syntax for labeling root nodes of trees in <code>DescribedComputions</code>
   */
  implicit class LabellingSyntax[Value](w: DescribedComputation[Value]) {
    /**
     * This method is syntactic sugar for <code>description ~< w</code>
     */
    def ~>(description: String) = description ~< w
  }

  implicit def logTreeShow(implicit annotationShow: Show[Annotation]) = new Show[LogTree] {
    override def shows(t: LogTree) = toList(t).map(line ⇒ "  " * line._1 + line._2).mkString(System.getProperty("line.separator"))

    private def toList(tree: LogTree, depth: Int = 0): List[(Int, String)] =
      line(depth, tree.rootLabel) :: tree.subForest.flatMap(toList(_, depth + 1)).toList

    private def line(depth: Int, label: LogTreeLabel[Annotation]) = (depth, showAnnotations(label.annotations, showSuccess(label.success, showDescription(label))))

    private def showAnnotations(annotations: Set[Annotation], line: String) =
      if (annotations.isEmpty) line else (line + " - [" + annotations.map(annotationShow.show(_)).mkString(", ") + "]")

    private def showDescription(label: LogTreeLabel[Annotation]) = label.fold(_.description, _ ⇒ "No Description")

    private def showSuccess(success: Boolean, s: String) = if (success) s else "Failed: " + s
  }
}