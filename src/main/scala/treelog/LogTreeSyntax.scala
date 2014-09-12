package treelog

import scalaz._
import scalaz.syntax.traverse._
import scalaz.syntax.monadTell._

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
  type LogTree = Tree[LogTreeLabel[Annotation]]
  type LogTreeWriter[Value] = Writer[LogTree, Value]
  type DescribedComputation[Value] = EitherT[LogTreeWriter, String, Value]

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

  private[treelog] trait EitherTFunctor[F[_], E] extends Functor[({type λ[α]=EitherT[F, E, α]})#λ] {
    implicit def F: Functor[F]

    override def map[A, B](fa: EitherT[F, E, A])(f: A => B): EitherT[F, E, B] = fa map f
  }

  private[treelog] trait EitherTMonad[F[_], E] extends Monad[({type λ[α]=EitherT[F, E, α]})#λ] with EitherTFunctor[F, E] {
    implicit def F: Monad[F]

    def point[A](a: => A): EitherT[F, E, A] = EitherT(F.point(\/-(a)))

    def bind[A, B](fa: EitherT[F, E, A])(f: A => EitherT[F, E, B]): EitherT[F, E, B] = fa flatMap f
  }

  private[treelog] trait EitherTHoist[A] extends Hoist[({type λ[α[_], β] = EitherT[α, A, β]})#λ] {
    def hoist[M[_], N[_]](f: M ~> N)(implicit M: Monad[M]) = new (({type λ[α] = EitherT[M, A, α]})#λ ~> ({type λ[α] = EitherT[N, A, α]})#λ) {
      def apply[B](mb: EitherT[M, A, B]): EitherT[N, A, B] = EitherT(f.apply(mb.run))
    }

    def liftM[M[_], B](mb: M[B])(implicit M: Monad[M]): EitherT[M, A, B] = EitherT(M.map(mb)(\/-(_)))

    implicit def apply[M[_] : Monad]: Monad[({type λ[α] = EitherT[M, A, α]})#λ] = EitherT.eitherTMonad
  }

  private[treelog] trait EitherTMonadTell[F[_, _], W, A] extends MonadTell[({type λ[α, β] = EitherT[({type f[x] = F[α, x]})#f, A, β]})#λ, W] with EitherTMonad[({type λ[α] = F[W, α]})#λ, A] with EitherTHoist[A] {
    def MT: MonadTell[F, W]

    implicit def F = MT

    def writer[B](w: W, v: B): EitherT[({type λ[α] = F[W, α]})#λ, A, B] =
      liftM[({type λ[α] = F[W, α]})#λ, B](MT.writer(w, v))

    def left[B](v: => A): EitherT[({type λ[α] = F[W, α]})#λ, A, B] =
      EitherT.left[({type λ[α] = F[W, α]})#λ, A, B](MT.point(v))

    def right[B](v: => B): EitherT[({type λ[α] = F[W, α]})#λ, A, B] =
      EitherT.right[({type λ[α] = F[W, α]})#λ, A, B](MT.point(v))
  }

  private[treelog] trait EitherTMonadListen[F[_, _], W, A] extends MonadListen[({type λ[α, β] = EitherT[({type f[x] = F[α, x]})#f, A, β]})#λ, W] with EitherTMonadTell[F, W, A] {
    implicit def MT: MonadListen[F, W]

    def listen[B](ma: EitherT[({type λ[α] = F[W, α]})#λ, A, B]): EitherT[({type λ[α] = F[W, α]})#λ, A, (B, W)] = {
      val tmp = MT.bind[(A \/ B, W), A \/ (B, W)](MT.listen(ma.run)){
        case (-\/(a), _) => MT.point(-\/(a))
        case (\/-(b), w) => MT.point(\/-((b, w)))
      }

      EitherT[({type λ[α] = F[W, α]})#λ, A, (B, W)](tmp)
    }
  }

  object MyEitherTFunctions {
    def eitherT[F[_], A, B](a: F[A \/ B]): EitherT[F, A, B] = EitherT[F, A, B](a)

    def monadTell[F[_, _], W, A](implicit MT0: MonadTell[F, W]): EitherTMonadTell[F, W, A] = new EitherTMonadTell[F, W, A]{
      def MT = MT0
    }

    def monadListen[F[_, _], W, A](implicit ML0: MonadListen[F, W]): EitherTMonadListen[F, W, A] = new EitherTMonadListen[F, W, A]{
      def MT = ML0
    }
  }

  private implicit val eitherWriter: EitherTMonadListen[Writer, LogTree, String] = MyEitherTFunctions.monadListen[Writer, LogTree, String]

  private def failure[Value](description: String, tree: LogTree): DescribedComputation[Value] =
    for {
      _ ← eitherWriter.tell(tree)
      err ← eitherWriter.left[Value](description)
    } yield err

  private def success[Value](value: Value, tree: LogTree): DescribedComputation[Value] = eitherWriter.right(value) :++>> (_ ⇒ tree)

  def failureLog[Value](dc: DescribedComputation[Value]): DescribedComputation[Value] = {
    val logTree = dc.run.written match {
      case Tree.Node(UndescribedLogTreeLabel(s, a), c) ⇒ Tree.node(UndescribedLogTreeLabel(false, a), c)
      case Tree.Node(DescribedLogTreeLabel(d, s, a), c) ⇒ Tree.node(DescribedLogTreeLabel(d, false, a), c)
    }
    dc.run.value match {
      case -\/(des) ⇒ failure(des, logTree)
      case \/-(a) ⇒ success(a, logTree)
    }
  }

  /**
   * Create a [[treelog.LogTreeSyntax.DescribedComputation]] representing a failure using the given `description` for both the log tree label and as
   * the content of the `value`, which will be a [[scalaz.-\/]].
   */
  def failure[Value](description: String): DescribedComputation[Value] = failure(description, Tree.leaf(DescribedLogTreeLabel(description, false)))

  /**
   * Create a [[treelog.LogTreeSyntax.DescribedComputation]] representing a success with the given `value` (lifted into a [[scalaz.\/-]]) and the given
   * `description` in the log tree.
   */
  def success[Value](value: Value, description: String): DescribedComputation[Value] =
    eitherWriter.right(value) :++>> (_ ⇒ Tree.leaf(DescribedLogTreeLabel(description, true, Set[Annotation]())))

  /**
   * Syntax for lifting values into `DescribedComputations` and creating leaf nodes in the log tree.
   */

  implicit class LeafSyntax[Value](value: Value) {

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
    def logSuccess(description: String): DescribedComputation[Value] = success(value, description)

    /**
     * Sugar for [[treelog.LogTreeSyntax.LeafSyntax.logSuccess(String) logSuccess]]
     */
    def ~>(description: String): DescribedComputation[Value] = logSuccess(description)

    /**
     * Create a ''success'' [[treelog.LogTreeSyntax.DescribedComputation]] with `\/-(value)` as the value and
     * a success [[treelog.LogTreeSyntax.DescribedComputation]] using the given `description` function to generate
     * a description for the tree node's label.
     *
     * {{{
     * import treelog.LogTreeSyntaxWithoutAnnotations._
     * import scalaz.syntax.show._
     *
     * val leaf = 1 logSuccess (x ⇒ s"One: $x")
     * println(result.run.value)
     * // Will print: \/-(1) - note that the 'right' means ''success''
     *
     * println(result.run.written.shows)
     * // Will print:
     * // One: 1
     * }}}
     */
    def logSuccess(description: Value ⇒ String): DescribedComputation[Value] = ~>(description(value))

    /**
     * Sugar for [[treelog.LogTreeSyntax.LeafSyntax.logSuccess(Value ⇒ String) logSuccess]]
     */
    def ~>(description: Value ⇒ String): DescribedComputation[Value] = logSuccess(description(value))

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
    def logFailure(description: String): DescribedComputation[Value] = failure(description)

    /**
     * Sugar for [[treelog.LogTreeSyntax.LeafSyntax.logFailure(String) logFailure]]
     */
    def ~>!(description: String): DescribedComputation[Value] = logFailure(description)

    /**
     * Create a ''failure'' [[treelog.LogTreeSyntax.DescribedComputation]] using the given `description` function to
     * generate a description for the tree node's label and for the `DescribedComputations` value (i.e.
     * the value will be `\/-(description(value))`.
     *
     * {{{
     * import treelog.LogTreeSyntaxWithoutAnnotations._
     * import scalaz.syntax.show._
     *
     * val leaf = 1 logFailure (x ⇒ s"One - $x")
     * println(result.run.value)
     * // Will print: -\/("One") - note that the 'left' means ''failure'', and the contained value is the description, not the 1.
     *
     * println(result.run.written.shows)
     * // Will print:
     * // Failed: One - 1
     * }}}
     */
    def logFailure(description: Value ⇒ String): DescribedComputation[Value] = logFailure(description(value))

    /**
     * Sugar for [[treelog.LogTreeSyntax.LeafSyntax.logFailure(Value ⇒ String) logFailure]]
     */
    def ~>!(description: Value ⇒ String): DescribedComputation[Value] = logFailure(description)
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
  implicit class AnnotationsSyntax[Value](w: DescribedComputation[Value]) {

    /**
     * Annotate a [[treelog.LogTreeSyntax.DescribedComputation DescribedComputation]].
     */
    def annotateWith(annotations: Set[Annotation]): DescribedComputation[Value] = {
      val newTree = w.run.written match {
        case Tree.Node(l: DescribedLogTreeLabel[Annotation], c) ⇒ Tree.node(l.copy(annotations = l.annotations ++ annotations), c)
        case Tree.Node(l: UndescribedLogTreeLabel[Annotation], c) ⇒ Tree.node(l.copy(annotations = l.annotations ++ annotations), c)
      }

      w.run.value match {
        case -\/(error) ⇒ failure(error, newTree)
        case \/-(value) ⇒ success(value, newTree)
      }
    }

    /**
     * Sugar for [[treelog.LogTreeSyntax.AnnotationsSyntax.annotateWith(Set[Annotation]) annotateWith(annotations)]]
     */
    def ~~(annotations: Set[Annotation]): DescribedComputation[Value] = annotateWith(annotations)

    /**
     * Equivalent to `~~ annotation`
     */
    def annotateWith(annotation: Annotation): DescribedComputation[Value] = ~~(Set(annotation))

    /**
     * Syntactic sugar equivalent to [[treelog.LogTreeSyntax.AnnotationsSyntax.annotateWith(Annotation) annotateWith(annotation)]]
     */
    def ~~(annotation: Annotation): DescribedComputation[Value] = annotateWith(annotation)


    /**
     * Get the union of all annotations in the log tree of the [[treelog.LogTreeSyntax.DescribedComputation DescribedComputation]].
     */
    def allAnnotations: Set[Annotation] = {
      def recurse(tree: LogTree, accumulator: Set[Annotation]): Set[Annotation] = tree.subForest.foldLeft(accumulator ++ tree.rootLabel.annotations)((acc, child) ⇒ recurse(child, acc))
      recurse(w.run.written, Set())
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
    def ~>?(failureDescription: ⇒ String, successDescription: ⇒ String): DescribedComputation[Boolean] =
      if (b) success(true, successDescription) else failure(failureDescription)
  }

  /**
   * Syntax for treating `Options` as indicators of success or failure in a computation.
   *
   * The simplest usage is something like: `myOption ~>? "Do I have Some?"`. The 'value'
   * and log tree of the returned [[treelog.LogTreeSyntax.DescribedComputation]] will indicate success or failure
   * depending on the value of `myOption`.
   */
  implicit class OptionSyntax[Value](option: Option[Value]) {

    /**
     * Use the same description whether the Option is `Some` or `None`.
     * Equivalent to `log(description, description)`
     */
    def log(description: String): DescribedComputation[Value] = log(description, description)

    /**
     * Sugar for [[treelog.LogTreeSyntax.OptionSyntax.log(String) log(String)]]
     */
    def ~>?(description: String): DescribedComputation[Value] = log(description)

    /**
     * Use different descriptions for the `Some` and `None` cases.
     *
     * If the option is `Some(x)` the 'value' of the returned DescribedComputation will be `\/-(x)`,
     * otherwise, the 'value' will be `-\/(noneDescription)`.
     */
    def log(noneDescription: ⇒ String, someDescription: ⇒ String): DescribedComputation[Value] = ~>?(noneDescription, _ ⇒ someDescription)

    /**
     * Sugar for [[treelog.LogTreeSyntax.OptionSyntax.log(String, String) log(String, String)]]
     */
    def ~>?(noneDescription: ⇒ String, someDescription: ⇒ String): DescribedComputation[Value] = log(noneDescription, someDescription)

    /**
     * Use different descriptions for the `Some` and `None` cases, providing the boxed `Some`
     * value to the function used to produce the description for the `Some` case, so that it can be included in the
     * description if you wish.
     *
     * If the option is `Some(x)` the 'value' of the returned DescribedComputation will be `\/-(x)`,
     * otherwise, the 'value' will be `-\/(noneDescription)`.
     */
    def log(noneDescription: ⇒ String, someDescription: Value ⇒ String): DescribedComputation[Value] =
      option map { a ⇒ success(a, someDescription(a)) } getOrElse failure(noneDescription)

    /**
     * Sugar for [[treelog.LogTreeSyntax.OptionSyntax.log() log(String, String)]]
     */
    def ~>?(noneDescription: ⇒ String, someDescription: Value ⇒ String): DescribedComputation[Value] = log(noneDescription, someDescription)

    /**
     * Return a default [[treelog.LogTreeSyntax.DescribedComputation]] if `option` is a `None`.
     *
     * If the option is `Some(x)` the 'value' of the returned DescribedComputation will be `\/-(Some(x))`,
     * otherwise, the returned [[treelog.LogTreeSyntax.DescribedComputation]] will be `dflt`.
     */
    def ~>|[B](f: Value ⇒ DescribedComputation[B], dflt: ⇒ DescribedComputation[Option[B]]): DescribedComputation[Option[B]] =
      option.map(f).map((v: DescribedComputation[B]) ⇒ v.map(w ⇒ Option(w))) getOrElse dflt
  }

  /**
   * Syntax for treating `scalaz.\/` as signifiers of success or failure in a computation.
   *
   * The simplest usage is something like: `myEither ~>? "Do I have the right?"`. The 'value'
   * and log tree of the returned [[treelog.LogTreeSyntax.DescribedComputation]] will indicate success or failure
   * depending on the value of `myEither`.
   */
  implicit class EitherSyntax[Value](either: \/[String, Value]) {

    /**
     * Use different descriptions depending on whether `either` is a `\/-` or a `-\/`.
     */
    def ~>?(leftDescription: String ⇒ String, rightDescription: ⇒ String): DescribedComputation[Value] =
      ~>?(leftDescription, _ ⇒ rightDescription)

    /**
     * Use the same description regardless of whether `either` is a `\/-` or a `-\/`.
     * Equivalent to: `~>?((error: String) ⇒ s"$description - $error", description)`
     */
    def ~>?(description: String): DescribedComputation[Value] = ~>?((error: String) ⇒ s"$description - $error", description)

    /**
     * Use the given description if `either` is a `\/-`. If `either` is
     * `-\/(message)`, use `message` as the description.
     */
    def ~>?(description: Value ⇒ String): DescribedComputation[Value] = ~>?((error: String) ⇒ error, description)

    /**
     * Use the given functions to provide descriptions depending on whether `either` is a
     * `\/-` or `-\/`
     */
    def ~>?(leftDescription: String ⇒ String, rightDescription: Value ⇒ String): DescribedComputation[Value] =
      either.fold(error ⇒ failure(leftDescription(error)), a ⇒ success(a, rightDescription(a)))
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
    def ~<[F[_] : Monad : Traverse, Value](describedComputations: F[DescribedComputation[Value]]): DescribedComputation[F[Value]] =
      ~<+(describedComputations, (x: F[Value]) ⇒ x)

    /**
     * As ~< but folding over the resulting F[Value] to yield R and return a DescribedComputation[R] with all the logs.
     *
     * For example, given l = List[DescribedComputation[Int]], and f = List[Int] ⇒ Int (say summing the list), then
     * `"Sum" ~&lt;+(l, f)` would return a DescribedComputation containing the sum of the elements of the list.
     */
    def ~<+[F[_] : Monad : Traverse, Value, R](describedComputations: F[DescribedComputation[Value]], f: F[Value] ⇒ R): DescribedComputation[R] = {
      val monad = implicitly[Monad[F]]
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
     * If `dc` has a log tree with an undescribed root node, give the root node the `description` but otherwise
     * leave it unchanged. If the log tree has a described root node, create a new root node above the existing one and give the
     * new root node the `description`. In both cases preserve the `value` and success/failure status.
     */
    def ~<[Value](dc: DescribedComputation[Value]): DescribedComputation[Value] =
      dc.run.value match {
        case -\/(_) ⇒ failure(description, branchHoister(dc.run.written, description))
        case \/-(value) ⇒ success(value, branchHoister(dc.run.written, description))
      }

    private def branchHoister(tree: LogTree, description: String): LogTree = tree match {
      case Tree.Node(l: UndescribedLogTreeLabel[Annotation], children) ⇒ Tree.node(DescribedLogTreeLabel(description, allSuccessful(children)), children)
      case Tree.Node(l: DescribedLogTreeLabel[Annotation], children) ⇒ Tree.node(DescribedLogTreeLabel(description, allSuccessful(List(tree))), Stream(tree))
    }

    private def allSuccessful(trees: Iterable[LogTree]) = trees.forall(_.rootLabel.success())
  }

  implicit class FoldSyntax[Value](values: Iterable[Value]) {

    /**
     * Starting with a given value and description, foldleft over an Iterable of values and 'add' them, describing each 'addition'.
     */
    def ~>/[R](description: String, initial: DescribedComputation[R], f: (R, Value) ⇒ DescribedComputation[R]): DescribedComputation[R] = {
      @scala.annotation.tailrec
      def recurse(remainingValues: Iterable[Value], partialResult: DescribedComputation[R]): DescribedComputation[R] =
        if (remainingValues.isEmpty) partialResult
        else
          partialResult.run.value match {
            case -\/(m) ⇒ partialResult
            case \/-(_) ⇒ recurse(remainingValues.tail, partialResult.flatMap(p ⇒ f(p, remainingValues.head)))
          }

      description ~< recurse(values, initial)
    }
  }

  /**
   * Syntax for dealing with traversable monads
   */
  implicit class TraversableMonadSyntax[F[_]: Monad: Traverse, Value](values: F[Value]) {

    /**
     * This method is syntactic sugar for `description ~< monad.map(values)(f)`
     */
    def ~>*[B](description: String, f: Value ⇒ DescribedComputation[B]): DescribedComputation[F[B]] = description ~< implicitly[Monad[F]].map(values)(f)
  }

  /**
   * Syntax for labeling root nodes of trees in `DescribedComputions`
   */
  implicit class LabellingSyntax[Value](w: DescribedComputation[Value]) {

    /**
     * This method is syntactic sugar for `description ~< w`
     */
    def ~>(description: String) = description ~< w
  }

  implicit def logTreeShow(implicit annotationShow: Show[Annotation]) = new Show[LogTree] {

    override def shows(t: LogTree) = toList(t).map(line ⇒ "  " * line._1 + line._2).mkString(System.getProperty("line.separator"))

    private def toList(tree: LogTree, depth: Int = 0): List[(Int, String)] =
      line(depth, tree.rootLabel) :: tree.subForest.flatMap(toList(_, depth + 1)).toList

    private def line(depth: Int, label: LogTreeLabel[Annotation]) = (depth, showAnnotations(label.annotations, showSuccess(label.success(), showDescription(label))))

    private def showAnnotations(annotations: Set[Annotation], line: String) =
      if (annotations.isEmpty) line else line + " - [" + annotations.map(annotationShow.show).mkString(", ") + "]"

    private def showDescription(label: LogTreeLabel[Annotation]) = label.fold(_.description, _ ⇒ "No Description")

    private def showSuccess(success: Boolean, s: String) = if (success) s else "Failed: " + s
  }

  type SerializableDescribedComputation[Value] = Pair[\/[String, Value], SerializableTree[Annotation]]

  def toSerializableForm[Value](dc: DescribedComputation[Value]): SerializableDescribedComputation[Value] = {
    def transform(tree: LogTree): SerializableTree[Annotation] = SerializableTree(tree.rootLabel, tree.subForest.map(transform).toList)
    Pair(dc.run.value, transform(dc.run.written))
  }

  def fromSerializableForm[Value](sdc: SerializableDescribedComputation[Value]): DescribedComputation[Value] = {
    def transform(tree: SerializableTree[Annotation]): LogTree = Tree.node(tree.label, tree.children.map(transform).toStream)
    sdc._1.fold(m ⇒ failure(m, transform(sdc._2)), v ⇒ success(v, transform(sdc._2)))
  }
}