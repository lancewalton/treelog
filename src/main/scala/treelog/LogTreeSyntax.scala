package treelog

import cats.data.{Writer, _}
import cats._
import cats.implicits._
import treelog.Tree.{Leaf, Node}
import treelog.ScalaCompat._

/**
  * See the [[treelog]] package documentation for a brief introduction to treelog and also,
  * [[https://github.com/lancewalton/treelog#using-treelog---examples examples on GitHub]] to get started.
  *
  * This trait provides syntax for manipulating `DescribedComputations`. Either:
  * <ul>
  *   <li>extend this trait, or</li>
  *   <li>define an object with the appropriate Annotation type and import on demand</li>
  * </ul>
  */
trait LogTreeSyntax[Annotation] {
  type LogTree                 = Tree[LogTreeLabel[Annotation]]
  type LogTreeWriter[V]        = Writer[LogTree, V]
  type DescribedComputation[V] = EitherT[LogTreeWriter, String, V]

  private val NilTree: LogTree = Leaf(UndescribedLogTreeLabel(true))

  implicit val logTreeMonoid: Monoid[LogTree] = new Monoid[LogTree] {

    val empty = NilTree

    def combine(augend: LogTree, addend: LogTree): LogTree =
      (augend, addend) match {
        case (NilTree, r) => r

        case (l, NilTree) => l

        case (Node(leftLabel: UndescribedLogTreeLabel[Annotation], leftChildren), Node(rightLabel: UndescribedLogTreeLabel[Annotation], rightChildren)) =>
          Node(UndescribedLogTreeLabel(leftLabel.success && rightLabel.success, leftLabel.annotations ++ rightLabel.annotations), leftChildren ++ rightChildren)

        case (Node(leftLabel: UndescribedLogTreeLabel[Annotation], leftChildren), rightNode @ Node(rightLabel, _)) =>
          Node(UndescribedLogTreeLabel(leftLabel.success && rightLabel.success, leftLabel.annotations), leftChildren :+ rightNode)

        case (leftNode @ Node(leftLabel, _), Node(rightLabel: UndescribedLogTreeLabel[Annotation], rightChildren)) =>
          Node(UndescribedLogTreeLabel(leftLabel.success && rightLabel.success, rightLabel.annotations), leftNode #:: rightChildren)

        case (leftNode: Tree[LogTreeLabel[Annotation]], rightNode: Tree[LogTreeLabel[Annotation]]) =>
          Node(UndescribedLogTreeLabel(leftNode.rootLabel.success && rightNode.rootLabel.success), LazyList(leftNode, rightNode))
      }
  }

  private def failure[V](description: String, tree: LogTree): DescribedComputation[V] =
    EitherT[LogTreeWriter, String, V](Writer(tree, description.asLeft[V]))

  private def success[V](value: V, tree: LogTree): DescribedComputation[V] =
    EitherT[LogTreeWriter, String, V](Writer(tree, value.asRight))

  def failureLog[V](dc: DescribedComputation[V]): DescribedComputation[V] = {
    val logTree: Tree[LogTreeLabel[Annotation]] = dc.value.written match {
      case Node(UndescribedLogTreeLabel(_, a), c) => Node(UndescribedLogTreeLabel(false, a), c)
      case Node(DescribedLogTreeLabel(d, _, a), c) => Node(DescribedLogTreeLabel(d, false, a), c)
      case Leaf(UndescribedLogTreeLabel(_, a)) => Leaf(UndescribedLogTreeLabel(false, a))
      case Leaf(DescribedLogTreeLabel(d, _, a)) => Leaf(DescribedLogTreeLabel(d, false, a))
    }
    dc.value.value match {
      case Left(des) => failure(des, logTree)
      case Right(a) => success(a, logTree)
    }
  }

  /**
    * Create a [[treelog.LogTreeSyntax.DescribedComputation]] representing a failure using the given `description` for both the log tree label and as
    * the content of the `value`, which will be a [[Left]].
    */
  def failure[V](description: String): DescribedComputation[V] =
    failure(description, Leaf(DescribedLogTreeLabel(description, false)))

  /**
    * Create a [[treelog.LogTreeSyntax.DescribedComputation]] representing a success with the given `value` (lifted into a [[Right]]) and the given
    * `description` in the log tree.
    */
  def success[V](value: V, description: String): DescribedComputation[V] =
    EitherT[LogTreeWriter, String, V](Writer(Leaf(DescribedLogTreeLabel(description, true, Set[Annotation]())), value.asRight))

  /**
    * Create a [[treelog.LogTreeSyntax.DescribedComputation]] representing a success with the given `value` (lifted into a [[Right]]) and no
    * description.
    */
  def success[V](value: V): DescribedComputation[V] =
    EitherT.pure(value)

  /**
    * Syntax for lifting values into `DescribedComputations` and creating leaf nodes in the log tree.
    */
  implicit class LeafSyntax[V](value: V) {

    /**
      * Create a ''success'' [[treelog.LogTreeSyntax.DescribedComputation]] with `\/-(value)` as the value and
      * a success [[treelog.LogTreeLabel TreeNode]] with the given `description`.
      *
      * {{{
      * import treelog.LogTreeSyntaxWithoutAnnotations._
      * import scalaz.syntax.show._
      *
      * val leaf = 1 logSuccess "One"
      * println(result.run.value)
      * // Will print: \/-(1) - note that the 'right' means ''success''
      *
      * println(result.run.written.shows)
      * // Will print:
      * // One
      * }}}
      */
    def logSuccess(description: String): DescribedComputation[V] = success(value, description)

    /**
      * Sugar for [[treelog.LogTreeSyntax.LeafSyntax.logSuccess(String) logSuccess]]
      */
    def ~>(description: String): DescribedComputation[V] = logSuccess(description)

    /**
      * Create a ''success'' [[treelog.LogTreeSyntax.DescribedComputation]] with `\/-(value)` as the value and
      * a success [[treelog.LogTreeSyntax.DescribedComputation]] using the given `description` function to generate
      * a description for the tree node's label.
      *
      * {{{
      * import treelog.LogTreeSyntaxWithoutAnnotations._
      * import scalaz.syntax.show._
      *
      * val leaf = 1 logSuccess (x => s"One: $x")
      * println(result.run.value)
      * // Will print: \/-(1) - note that the 'right' means ''success''
      *
      * println(result.run.written.shows)
      * // Will print:
      * // One: 1
      * }}}
      */
    def logSuccess(description: V => String): DescribedComputation[V] = ~>(description(value))

    /**
      * Sugar for [[treelog.LogTreeSyntax.LeafSyntax.logSuccess(V => String) logSuccess]]
      */
    def ~>(description: V => String): DescribedComputation[V] = logSuccess(description(value))

    /**
      * Create a ''failure'' [[treelog.LogTreeSyntax.DescribedComputation]] with `-\/(description)` as the value and
      * a failure [[treelog.LogTreeSyntax.DescribedComputation]] with the given `description`.
      *
      * {{{
      * import treelog.LogTreeSyntaxWithoutAnnotations._
      * import scalaz.syntax.show._
      *
      * val leaf = 1 ~>! "One"
      * println(result.run.value)
      * // Will print: -\/("One") - note that the 'left' means ''failure'', and the contained value is the description, not the 1.
      *
      * println(result.run.written.shows)
      * // Will print:
      * // Failed: One
      * }}}
      */
    def logFailure(description: String): DescribedComputation[V] = failure(description)

    /**
      * Sugar for [[treelog.LogTreeSyntax.LeafSyntax.logFailure(String) logFailure]]
      */
    def ~>!(description: String): DescribedComputation[V] = logFailure(description)

    /**
      * Create a ''failure'' [[treelog.LogTreeSyntax.DescribedComputation]] using the given `description` function to
      * generate a description for the tree node's label and for the `DescribedComputations` value (i.e.
      * the value will be `\/-(description(value))`.
      *
      * {{{
      * import treelog.LogTreeSyntaxWithoutAnnotations._
      * import scalaz.syntax.show._
      *
      * val leaf = 1 logFailure (x => s"One - $x")
      * println(result.run.value)
      * // Will print: -\/("One") - note that the 'left' means ''failure'', and the contained value is the description, not the 1.
      *
      * println(result.run.written.shows)
      * // Will print:
      * // Failed: One - 1
      * }}}
      */
    def logFailure(description: V => String): DescribedComputation[V] = logFailure(description(value))

    /**
      * Sugar for [[treelog.LogTreeSyntax.LeafSyntax.logFailure(V => String) logFailure]]
      */
    def ~>!(description: V => String): DescribedComputation[V] = logFailure(description)
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
    * // Will print: \/-(1) - note that the 'right' means ''success''
    *
    * println(result.run.written.shows)
    * // Will print:
    * // One - [Annotating with a string, And another]
    * }}}
    */
  implicit class AnnotationsSyntax[V](w: DescribedComputation[V]) {

    /**
      * Annotate a [[treelog.LogTreeSyntax.DescribedComputation DescribedComputation]].
      */
    def annotateWith(annotations: Set[Annotation]): DescribedComputation[V] = {
      val newTree: Tree[LogTreeLabel[Annotation]] = w.value.written match {
        case Node(l: DescribedLogTreeLabel[Annotation], c) => Node(l.copy(annotations   = l.annotations ++ annotations), c)
        case Node(l: UndescribedLogTreeLabel[Annotation], c) => Node(l.copy(annotations = l.annotations ++ annotations), c)
        case Leaf(l: DescribedLogTreeLabel[Annotation]) => Leaf(l.copy(annotations   = l.annotations ++ annotations))
        case Leaf(l: UndescribedLogTreeLabel[Annotation]) => Leaf(l.copy(annotations = l.annotations ++ annotations))
      }

      w.value.value match {
        case Left(error) => failure(error, newTree)
        case Right(value) => success(value, newTree)
      }
    }

    /**
      * Sugar for [[treelog.LogTreeSyntax.AnnotationsSyntax.annotateWith(Set[Annotation]) annotateWith(annotations)]]
      */
    def ~~(annotations: Set[Annotation]): DescribedComputation[V] = annotateWith(annotations)

    /**
      * Equivalent to `~~ annotation`
      */
    def annotateWith(annotation: Annotation): DescribedComputation[V] = ~~(Set(annotation))

    /**
      * Syntactic sugar equivalent to [[treelog.LogTreeSyntax.AnnotationsSyntax.annotateWith(Annotation) annotateWith(annotation)]]
      */
    def ~~(annotation: Annotation): DescribedComputation[V] = annotateWith(annotation)

    /**
      * Get the union of all annotations in the log tree of the [[treelog.LogTreeSyntax.DescribedComputation DescribedComputation]].
      */
    def allAnnotations: Set[Annotation] = {
      def recurse(tree: LogTree, accumulator: Set[Annotation]): Set[Annotation] = tree.subForest.foldLeft(accumulator ++ tree.rootLabel.annotations)((acc, child) => recurse(child, acc))
      recurse(w.value.written, Set())
    }
  }

  /**
    * Syntax for treating booleans as indicators of success or failure in a computation.
    *
    * The simplest usage is something like: `myBoolean ~>? "Is my boolean true?"`. The 'value'
    * and log tree of the returned [[treelog.LogTreeSyntax.DescribedComputation]] will indicate success or failure
    * depending on the value of `myBoolean`.
    */
  implicit class BooleanSyntax(b: Boolean) {

    /**
      * Use the same description whether the boolean is `true` or `false`.
      * Equivalent to `~>?(description, description)`
      */
    def ~>?(description: String): DescribedComputation[Boolean] = ~>?(description, description)

    /**
      * Use different descriptions for the `true` and `false` cases. Note that unlike `'if'`
      * the `false` / failure description is the first parameter and the `true` / success
      * description is the second parameter. This is to maintain consistency with [[treelog.LogTreeSyntax.OptionSyntax OptionSyntax]]
      * and [[treelog.LogTreeSyntax.EitherSyntax EitherSyntax]].
      *
      * If the boolean is `true` the 'value' of the returned DescribedComputation will be `\/-(true)`,
      * otherwise, the 'value' will be `-\/(description)`.
      */
    def ~>?(failureDescription: => String, successDescription: => String): DescribedComputation[Boolean] =
      if (b) success(true, successDescription) else failure(failureDescription)
  }

  /**
    * Syntax for treating `Options` as indicators of success or failure in a computation.
    *
    * The simplest usage is something like: `myOption ~>? "Do I have Some?"`. The 'value'
    * and log tree of the returned [[treelog.LogTreeSyntax.DescribedComputation]] will indicate success or failure
    * depending on the value of `myOption`.
    */
  implicit class OptionSyntax[V](option: Option[V]) {

    /**
      * Use the same description whether the Option is `Some` or `None`.
      * Equivalent to `log(description, description)`
      */
    def log(description: String): DescribedComputation[V] = log(description, description)

    /**
      * Sugar for [[treelog.LogTreeSyntax.OptionSyntax.log(String) log(String)]]
      */
    def ~>?(description: String): DescribedComputation[V] = log(description)

    /**
      * Use different descriptions for the `Some` and `None` cases.
      *
      * If the option is `Some(x)` the 'value' of the returned DescribedComputation will be `\/-(x)`,
      * otherwise, the 'value' will be `-\/(noneDescription)`.
      */
    def log(noneDescription: => String, someDescription: => String): DescribedComputation[V] = ~>?(noneDescription, _ => someDescription)

    /**
      * Sugar for [[treelog.LogTreeSyntax.OptionSyntax.log(String, String) log(String, String)]]
      */
    def ~>?(noneDescription: => String, someDescription: => String): DescribedComputation[V] = log(noneDescription, someDescription)

    /**
      * Use different descriptions for the `Some` and `None` cases, providing the boxed `Some`
      * value to the function used to produce the description for the `Some` case, so that it can be included in the
      * description if you wish.
      *
      * If the option is `Some(x)` the 'value' of the returned DescribedComputation will be `\/-(x)`,
      * otherwise, the 'value' will be `-\/(noneDescription)`.
      */
    def log(noneDescription: => String, someDescription: V => String): DescribedComputation[V] =
      option map { a =>
        success(a, someDescription(a))
      } getOrElse failure(noneDescription)

    /**
      * Sugar for [[treelog.LogTreeSyntax.OptionSyntax.log() log(String, String)]]
      */
    def ~>?(noneDescription: => String, someDescription: V => String): DescribedComputation[V] = log(noneDescription, someDescription)

    /**
      * Return a default [[treelog.LogTreeSyntax.DescribedComputation]] if `option` is a `None`.
      *
      * If the option is `Some(x)` the 'value' of the returned DescribedComputation will be `\/-(Some(x))`,
      * otherwise, the returned [[treelog.LogTreeSyntax.DescribedComputation]] will be `dflt`.
      */
    def ~>|[B](f: V => DescribedComputation[B], dflt: => DescribedComputation[Option[B]]): DescribedComputation[Option[B]] =
      option.map(f).map((v: DescribedComputation[B]) => v.map(w => Option(w))) getOrElse dflt
  }

  /**
    * Syntax for treating `scalaz.\/` as signifiers of success or failure in a computation.
    *
    * The simplest usage is something like: `myEither ~>? "Do I have the right?"`. The 'value'
    * and log tree of the returned [[treelog.LogTreeSyntax.DescribedComputation]] will indicate success or failure
    * depending on the value of `myEither`.
    */
  implicit class EitherSyntax[V](either: Either[String, V]) {

    /**
      * Use different descriptions depending on whether `either` is a `\/-` or a `-\/`.
      */
    def ~>?(leftDescription: String => String, rightDescription: => String): DescribedComputation[V] =
      ~>?(leftDescription, _ => rightDescription)

    /**
      * Use the same description regardless of whether `either` is a `\/-` or a `-\/`.
      * Equivalent to: `~>?((error: String) => s"$description - $error", description)`
      */
    def ~>?(description: String): DescribedComputation[V] = ~>?((error: String) => s"$description - $error", description)

    /**
      * Use the given description if `either` is a `\/-`. If `either` is
      * `-\/(message)`, use `message` as the description.
      */
    def ~>?(description: V => String): DescribedComputation[V] = ~>?((error: String) => error, description)

    /**
      * Use the given functions to provide descriptions depending on whether `either` is a
      * `\/-` or `-\/`
      */
    def ~>?(leftDescription: String => String, rightDescription: V => String): DescribedComputation[V] =
      either.fold(error => failure(leftDescription(error)), a => success(a, rightDescription(a)))
  }

  /**
    * Syntax for labeling or creating new branches in a log tree given a description.
    */
  implicit class BranchLabelingSyntax(description: String) {

    /**
      * Create a new branch given a monadic, traversable 'container' `F[DescribedComputation[Value]]`, 'sequence' it
      * to create a `DescribedComputation[F[Value]]`, and give the new `DescribedComputation's` log tree a
      * new root node, with the given `description` and whose children are the trees in the
      * `F[DescribedComputation[Value]]`.
      *
      * For example, if we evaluate this method with `F` instantiated as `List`, we would turn a
      * `List[DescribedComputation[Value]]` into a `DescribedComputation[List[Value]]`, such that the
      * `List[Value]` which is the result's 'value' is obtained by extracting the 'value' from each
      * [[treelog.LogTreeSyntax.DescribedComputation]] in the `describedComputations` parameter. Likewise, the child nodes
      * of the returned log tree root node are obtained by extracting the log tree from each of the `describedComputations`.
      *
      * The ''success'' status of the returned `DescribedComputations` log tree is `true` if all of the children
      * are successful. It is `false` otherwise.
      */
    def ~<[F[_]: Monad: Traverse, V](describedComputations: F[DescribedComputation[V]]): DescribedComputation[F[V]] =
      ~<+(describedComputations, (x: F[V]) => x)

    /**
      * As ~< but folding over the resulting F[Value] to yield R and return a DescribedComputation[R] with all the logs.
      *
      * For example, given l = List[DescribedComputation[Int]], and f = List[Int] => Int (say summing the list), then
      * `"Sum" ~&lt;+(l, f)` would return a DescribedComputation containing the sum of the elements of the list.
      */
    def ~<+[F[_]: Monad: Traverse, V, R](describedComputations: F[DescribedComputation[V]], f: F[V] => R): DescribedComputation[R] = {
      val monad = implicitly[Monad[F]]
      val parts = monad.map(describedComputations)(m => (m.value.value, m.value.written))

      val children = monad.map(parts)(_._2).toList
      val branch   = Node(DescribedLogTreeLabel(description, allSuccessful(children), Set[Annotation]()), children.toLazyList)

      describedComputations.sequence.value.run._2 match {
        case Left(_) => failure(description, branch)
        case Right(v) => success(f(v), branch)
      }
    }

    /**
      * If `dc` has a log tree with an undescribed root node, give the root node the `description` but otherwise
      * leave it unchanged. If the log tree has a described root node, create a new root node above the existing one and give the
      * new root node the `description`. In both cases preserve the `value` and success/failure status.
      */
    def ~<[V](dc: DescribedComputation[V]): DescribedComputation[V] =
      dc.value.value match {
        case Left(_) => failure(description, branchHoister(dc.value.written, description))
        case Right(value) => success(value, branchHoister(dc.value.written, description))
      }

    private def branchHoister(tree: LogTree, description: String): LogTree = tree match {
      case Node(l: UndescribedLogTreeLabel[Annotation], children) => Node(DescribedLogTreeLabel(description, allSuccessful(children), l.annotations), children)
      case Node(_: DescribedLogTreeLabel[Annotation], _) => Node(DescribedLogTreeLabel(description, allSuccessful(List(tree))), LazyList(tree))
      case Leaf(l: UndescribedLogTreeLabel[Annotation]) => Leaf(DescribedLogTreeLabel(description, tree.rootLabel.success, l.annotations))
      case Leaf(_: DescribedLogTreeLabel[Annotation]) => Node(DescribedLogTreeLabel(description, tree.rootLabel.success), LazyList(tree))
    }

    private def allSuccessful(trees: Iterable[LogTree]) = trees.forall(_.rootLabel.success)
  }

  implicit class FoldSyntax[V](values: Iterable[V]) {

    /**
      * Starting with a given value and description, foldleft over an Iterable of values and 'add' them, describing each 'addition'.
      */
    def ~>/[R](description: String, initial: DescribedComputation[R], f: (R, V) => DescribedComputation[R]): DescribedComputation[R] = {
      @scala.annotation.tailrec
      def recurse(remainingValues: Iterable[V], partialResult: DescribedComputation[R]): DescribedComputation[R] =
        if (remainingValues.isEmpty) partialResult
        else
          partialResult.value.value match {
            case Left(_) => partialResult
            case Right(_) => recurse(remainingValues.tail, partialResult.flatMap(p => f(p, remainingValues.head)))
          }

      val value: DescribedComputation[R] = recurse(values, initial)
      println(value.value.written)
      description ~< value
    }
  }

  /**
    * Syntax for dealing with traversable monads
    */
  implicit class TraversableMonadSyntax[F[_]: Monad: Traverse, V](values: F[V]) {

    /**
      * This method is syntactic sugar for `description ~< monad.map(values)(f)`
      */
    def ~>*[B](description: String, f: V => DescribedComputation[B]): DescribedComputation[F[B]] = description ~< implicitly[Monad[F]].map(values)(f)
  }

  /**
    * Syntax for labeling root nodes of trees in `DescribedComputions`
    */
  implicit class LabellingSyntax[V](w: DescribedComputation[V]) {

    /**
      * This method is syntactic sugar for `description ~< w`
      */
    def ~>(description: String) = description ~< w
  }

  implicit def logTreeShow(implicit annotationShow: Show[Annotation]): Show[LogTree] = new Show[LogTree] {

    override def show(t: LogTree): String =
      toList(t).map(line => "  " * line._1 + line._2).mkString(System.getProperty("line.separator"))

    private def toList(tree: LogTree, depth: Int = 0): List[(Int, String)] =
      line(depth, tree.rootLabel) :: tree.subForest.flatMap(toList(_, depth + 1)).toList

    private def line(depth: Int, label: LogTreeLabel[Annotation]) = (depth, showAnnotations(label.annotations, showSuccess(label.success, showDescription(label))))

    private def showAnnotations(annotations: Set[Annotation], line: String) =
      if (annotations.isEmpty) line else line + " - [" + annotations.map(annotationShow.show).mkString(", ") + "]"

    private def showDescription(label: LogTreeLabel[Annotation]) = label.fold(_.description, _ => "No Description")

    private def showSuccess(success: Boolean, s: String) = if (success) s else "Failed: " + s
  }

  type SerializableDescribedComputation[V] = (Either[String, V], SerializableTree[Annotation])

  def toSerializableForm[V](dc: DescribedComputation[V]): SerializableDescribedComputation[V] = {
    def transform(tree: LogTree): SerializableTree[Annotation] = SerializableTree(tree.rootLabel, tree.subForest.map(transform).toList)
    Tuple2(dc.value.value, transform(dc.value.written))
  }

  def fromSerializableForm[V](sdc: SerializableDescribedComputation[V]): DescribedComputation[V] = {
    def transform(tree: SerializableTree[Annotation]): LogTree = Node(tree.label, tree.children.map(transform).toLazyList)
    sdc._1.fold(m => failure(m, transform(sdc._2)), v => success(v, transform(sdc._2)))
  }
}
