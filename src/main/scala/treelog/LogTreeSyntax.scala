package treelog

import scalaz.{ -\/, \/-, \/, EitherT, Functor, Traverse, Monad, Monoid, Show, Writer, idInstance }
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

  implicit val logTreeMonoid = new Monoid[LogTree] {
    val zero = NilTree

    def append(augend: LogTree, addend: ⇒ LogTree): LogTree =
      (augend, addend) match {
        case (NilTree, r) ⇒ r

        case (l, NilTree) ⇒ l

        case (TreeNode(leftLabel: UndescribedLogTreeLabel[Annotation], leftChildren), TreeNode(rightLabel: UndescribedLogTreeLabel[Annotation], rightChildren)) ⇒
          TreeNode(UndescribedLogTreeLabel(leftLabel.success && rightLabel.success, leftLabel.annotations ++ rightLabel.annotations), leftChildren ::: rightChildren)

        case (TreeNode(leftLabel: UndescribedLogTreeLabel[Annotation], leftChildren), rightNode @ TreeNode(rightLabel, rightChildren)) ⇒
          TreeNode(UndescribedLogTreeLabel(leftLabel.success && rightLabel.success), leftChildren :+ rightNode)

        case (leftNode @ TreeNode(leftLabel, leftChildren), TreeNode(rightLabel: UndescribedLogTreeLabel[Annotation], rightChildren)) ⇒
          TreeNode(UndescribedLogTreeLabel(leftLabel.success && rightLabel.success), leftNode :: rightChildren)

        case (leftNode: TreeNode[LogTreeLabel[Annotation]], rightNode: TreeNode[LogTreeLabel[Annotation]]) ⇒
          TreeNode(UndescribedLogTreeLabel(leftNode.label.success && rightNode.label.success), List(augend, addend))
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
      case NilTree ⇒ NilTree
      case TreeNode(UndescribedLogTreeLabel(s, a), c) ⇒ TreeNode(UndescribedLogTreeLabel(false, a), c)
      case TreeNode(DescribedLogTreeLabel(d, s, a), c) ⇒ TreeNode(DescribedLogTreeLabel(d, false, a), c)
    }
    dc.run.value match {
      case -\/(des) ⇒ failure(des, logTree)
      case \/-(a) ⇒ success(a, logTree)
    }
  }

  /**
   * Create a <code>DescribedComputation</code> representing a failure using the given <code>description</code> for both the log tree label and as
   * the content of the <code>value</code>, which will be a [[scalaz.-\/]].
   */
  def failure[Value](description: String): DescribedComputation[Value] = failure(description, TreeNode(DescribedLogTreeLabel(description, false)))

  /**
   * Create a <code>DescribedComputation</code> representing a success with the given <code>value</code> (lifted into a [[scalaz.\/-]]) and the given
   * <code>description</code> in the log tree.
   */
  def success[Value](value: Value, description: String): DescribedComputation[Value] =
    success(value, TreeNode(DescribedLogTreeLabel(description, true, Set[Annotation]())))

  /**
   * Syntax for allowing annotations to be added to log tree nodes.
   *
   * The best way to see how this syntax works is to take a look at the
   * [[https://github.com/lancewalton/treelog#annotations annotations example]] on GitHub
   */
  implicit class AnnotationsSyntax[Value](w: DescribedComputation[Value]) {
    /**
     * Add a set of annotations to a node. For example:
     * {{{
     * val foo = 1 ~> "The value is one" ~~ Set("Annotating with a string", "And another")
     * }}}
     *
     * The value of the <code>DescribedComputation</code> will be 1, the description in the tree node label will be
     * "The value is one" and the label will also contain two annotations: "Annotating with a string" and "And another"
     */
    def ~~(annotations: Set[Annotation]): DescribedComputation[Value] = {
      val newTree = w.run.written match {
        case NilTree ⇒ NilTree
        case TreeNode(l: DescribedLogTreeLabel[Annotation], c) ⇒ TreeNode(l.copy(annotations = l.annotations ++ annotations), c)
        case TreeNode(l: UndescribedLogTreeLabel[Annotation], c) ⇒ TreeNode(l.copy(annotations = l.annotations ++ annotations), c)
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
      def recurse(tree: LogTree, accumulator: Set[Annotation]): Set[Annotation] = {
        tree match {
          case NilTree ⇒ accumulator
          case t: TreeNode[LogTreeLabel[Annotation]] ⇒ t.children.foldLeft(accumulator ++ t.label.annotations)((acc, child) ⇒ recurse(child, acc))
        }
      }
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
    def ~>?(noneDescription: ⇒ String, someDescription: ⇒ String): DescribedComputation[Value] =
      ~>?(noneDescription, _ ⇒ someDescription)

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

  implicit class EitherSyntax[Value](either: \/[String, Value]) {
    def ~>?(leftDescription: String ⇒ String, rightDescription: ⇒ String): DescribedComputation[Value] =
      ~>?(leftDescription, _ ⇒ rightDescription)

    def ~>?(description: String): DescribedComputation[Value] =
      ~>?((error: String) ⇒ s"$description - $error", description)

    def ~>?(description: Value ⇒ String): DescribedComputation[Value] =
      ~>?((error: String) ⇒ error, description)

    def ~>?(leftDescription: String ⇒ String, rightDescription: Value ⇒ String): DescribedComputation[Value] =
      either.fold(error ⇒ failure(leftDescription(error)), a ⇒ success(a, rightDescription(a)))
  }

  implicit class BranchLabelingSyntax(description: String) {
    def ~<[F[_], Value](mapped: F[DescribedComputation[Value]])(implicit monad: Monad[F], traverse: Traverse[F]): DescribedComputation[F[Value]] = {
      val parts = monad.map(mapped)(m ⇒ (m.run.value, m.run.written))

      val children = monad.map(parts)(_._2).toList
      val branch = TreeNode(
        DescribedLogTreeLabel(
          description,
          allSuccessful(children),
          Set[Annotation]()),
        children)

      mapped.sequence.run.value match {
        case -\/(_) ⇒ failure(description, branch)
        case \/-(v) ⇒ success(v, branch)
      }
    }
  }

  implicit class TraversableMonadSyntax[F[_], Value](values: F[Value])(implicit monad: Monad[F], traverse: Traverse[F]) {
    def ~>*[B](description: String, f: Value ⇒ DescribedComputation[B]): DescribedComputation[F[B]] = description ~< monad.map(values)(f)
  }

  implicit class LeafSyntax[Value](value: Value) {
    def ~>(description: String): DescribedComputation[Value] = success(value, description)
    def ~>(description: Value ⇒ String): DescribedComputation[Value] = ~>(description(value))
    def ~>!(description: String): DescribedComputation[Value] = failure(description)
    def ~>!(description: Value ⇒ String): DescribedComputation[Value] = ~>!(description(value))
  }

  private def allSuccessful(trees: Iterable[LogTree]) =
    trees.forall {
      _ match {
        case NilTree ⇒ true
        case TreeNode(l, _) ⇒ l.success
      }
    }

  implicit def branchSyntax(description: String) = new {
    def ~<[Value](ew: DescribedComputation[Value]): DescribedComputation[Value] =
      ew.run.value match {
        case -\/(error) ⇒ failure(error, branchHoister(ew.run.written, description))
        case \/-(value) ⇒ success(value, branchHoister(ew.run.written, description))
      }

    private def branchHoister(tree: LogTree, description: String, forceSuccess: Boolean = false): LogTree = tree match {
      case NilTree ⇒ TreeNode(DescribedLogTreeLabel(description, true))
      case TreeNode(l: UndescribedLogTreeLabel[Annotation], children) ⇒ TreeNode(DescribedLogTreeLabel(description, forceSuccess || allSuccessful(children)), children)
      case TreeNode(l: DescribedLogTreeLabel[Annotation], children) ⇒ TreeNode(DescribedLogTreeLabel(description, forceSuccess || allSuccessful(List(tree))), List(tree))
    }
  }

  implicit class LabellingSyntax[Value](w: DescribedComputation[Value]) {
    def ~>(description: String) = description ~< w
  }

  implicit def logTreeShow(implicit annotationShow: Show[Annotation]) = new Show[LogTree] {
    override def shows(t: LogTree) = toList(t).map(line ⇒ "  " * line._1 + line._2).mkString(System.getProperty("line.separator"))

    private def toList(tree: LogTree, depth: Int = 0): List[(Int, String)] =
      tree match {
        case NilTree ⇒ List((depth, "NilTree"))
        case TreeNode(label, children) ⇒ line(depth, label) :: children.flatMap(toList(_, depth + 1))
      }

    private def line(depth: Int, label: LogTreeLabel[Annotation]) = (depth, showAnnotations(label.annotations, showSuccess(label.success, showDescription(label))))

    private def showAnnotations(annotations: Set[Annotation], line: String) =
      if (annotations.isEmpty) line else (line + " - [" + annotations.map(annotationShow.show(_)).mkString(", ") + "]")

    private def showDescription(label: LogTreeLabel[Annotation]) = label.fold(l ⇒ l.description, l ⇒ "No Description")

    private def showSuccess(success: Boolean, s: String) = if (success) s else "Failed: " + s
  }
}