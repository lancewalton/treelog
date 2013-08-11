package treelog

import scalaz.{ -\/, \/-, \/, EitherT, Functor, Traverse, Monad, Monoid, Show, Writer, idInstance }
import scalaz.syntax.foldable
import scalaz.syntax.traverse._
import scalaz.syntax.monadListen._
import scala.annotation.tailrec

/**
 * TreeLog enables logging as a tree structure so that comprehensive logging does not become incomprehensible.
 *
 * It is often necessary to understand exactly what happened in a computation, not just that is succeeded or failed, but what was actually done
 * and with what data.
 * TreeLog is an attempt to produce a description of the computation, which is a hierarchical log of the processing that led to the result.
 *
 * Nodes in the log tree can be annotated with important information for your program to use later. This is useful, for example, when you want to audit
 * a process that affects multiple entities, and you want to make sure that the audit trail is associated with each of the modified entities. You can use
 * the annotation facility to carry the key (or something richer) for each modified entity.
 *
 * This trait provides syntax for manipulating <code>DescribedComputations</code>. Either:
 * <ul>
 *   <li>extend this trait, or</li>
 *   <li>define an object with the appropriate Annotation type and import on demand</li>
 * </ul>
 *
 * Please look at the [[https://github.com/lancewalton/treelog#using-treelog---examples examples on GitHub]] to get started.
 *
 * When a computation result is 'lifted' into a [[treelog.LogTreeSyntax]].DescribedComputation by one of the many methods in this trait, it is possible
 * to retrieve the 'value' of the computation like this:
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
   * Create a failure [[treelog.LogTreeSyntax]].DescribedComputation using the given <code>description</code> for both the log tree label and as the content of the
   * <code>value</code>, which will be a <code>scalaz.-\/</code>.
   */
  def failure[Value](description: String): DescribedComputation[Value] = failure(description, TreeNode(DescribedLogTreeLabel(description, false)))

  /**
   * Create a success [[treelog.LogTreeSyntax]].DescribedComputation with the given <code>value</code> (lifted into a <code>scalaz.\/-) and the given
   * <code>description</code> in the log tree.
   */
  def success[Value](value: Value, description: String): DescribedComputation[Value] =
    success(value, TreeNode(DescribedLogTreeLabel(description, true, Set[Annotation]())))

  /**
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

  implicit class BooleanSyntax(b: Boolean) {
    def ~>?(description: String): DescribedComputation[Boolean] =
      ~>?(description, description)

    def ~>?(failureDescription: ⇒ String, successDescription: ⇒ String): DescribedComputation[Boolean] =
      if (b) success(true, successDescription) else failure(failureDescription)
  }

  implicit class OptionSyntax[Value](option: Option[Value]) {
    def ~>?(description: String): DescribedComputation[Value] = ~>?(description, description)

    def ~>?(noneDescription: ⇒ String, someDescription: ⇒ String): DescribedComputation[Value] =
      ~>?(noneDescription, _ ⇒ someDescription)

    def ~>?(noneDescription: ⇒ String, someDescription: Value ⇒ String): DescribedComputation[Value] =
      option map { a ⇒ success(a, someDescription(a)) } getOrElse failure(noneDescription)

    def ~>|[B](f: Value ⇒ DescribedComputation[B], g: ⇒ DescribedComputation[Option[B]]): DescribedComputation[Option[B]] =
      option.map(f).map((v: DescribedComputation[B]) ⇒ v.map(w ⇒ Some(w))) getOrElse g
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

  implicit class DescriptionSyntax(description: String) {

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