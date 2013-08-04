package treelog

import scalaz.{ -\/, \/-, \/, EitherT, Monoid, Show, Writer, idInstance }
import scalaz.syntax.monadListen._

sealed trait LogTreeLabel[+R] {
  def references: R
  def success(): Boolean
  def fold[T](f: DescribedLogTreeLabel[R] ⇒ T, g: UndescribedLogTreeLabel[R] ⇒ T): T
}

case class DescribedLogTreeLabel[+R](description: String, success: Boolean, references: R) extends LogTreeLabel[R] {
  def fold[T](f: DescribedLogTreeLabel[R] ⇒ T, g: UndescribedLogTreeLabel[R] ⇒ T) = f(this)
}

case class UndescribedLogTreeLabel[+R](success: Boolean, references: R) extends LogTreeLabel[R] {
  def fold[T](f: DescribedLogTreeLabel[R] ⇒ T, g: UndescribedLogTreeLabel[R] ⇒ T) = g(this)
}

sealed trait Tree[+Label]
case object NilTree extends Tree[Nothing]
case class TreeNode[Label](label: Label, children: List[Tree[Label]] = Nil) extends Tree[Label]

trait LogTreeSyntax[R] {
  type LogTree = Tree[LogTreeLabel[R]]
  type LogTreeWriter[+V] = Writer[LogTree, V]
  type DescribedComputation[+V] = EitherT[LogTreeWriter, String, V]

  val referencesMonoid: Monoid[R]

  implicit val logTreeMonoid = new Monoid[LogTree] {
    val zero = NilTree

    def append(augend: LogTree, addend: ⇒ LogTree): LogTree =
      (augend, addend) match {
        case (NilTree, r) ⇒ r

        case (l, NilTree) ⇒ l

        case (TreeNode(UndescribedLogTreeLabel(leftSuccess, leftData), leftChildren), TreeNode(UndescribedLogTreeLabel(rightSuccess, rightData), rightChildren)) ⇒
          TreeNode(UndescribedLogTreeLabel(leftSuccess && rightSuccess, referencesMonoid.append(leftData, rightData)), leftChildren ::: rightChildren)

        case (TreeNode(UndescribedLogTreeLabel(leftSuccess, leftData), leftChildren), rightNode @ TreeNode(rightLabel, rightChildren)) ⇒
          TreeNode(UndescribedLogTreeLabel(leftSuccess && rightLabel.success, referencesMonoid.append(leftData, rightLabel.references)), leftChildren :+ rightNode)

        case (leftNode @ TreeNode(leftLabel, leftChildren), TreeNode(UndescribedLogTreeLabel(rightSuccess, rightData), rightChildren)) ⇒
          TreeNode(UndescribedLogTreeLabel(leftLabel.success && rightSuccess, referencesMonoid.append(leftLabel.references, rightData)), leftNode :: rightChildren)

        case (leftNode: TreeNode[LogTreeLabel[R]], rightNode: TreeNode[LogTreeLabel[R]]) ⇒
          TreeNode(UndescribedLogTreeLabel(leftNode.label.success && rightNode.label.success, referencesMonoid.append(leftNode.label.references, rightNode.label.references)), List(augend, addend))
      }
  }

  private implicit val eitherWriter = EitherT.monadListen[Writer, LogTree, String]

  private def failure[A](description: String, tree: LogTree): DescribedComputation[A] =
    for {
      _ ← eitherWriter.tell(tree)
      err ← eitherWriter.left(description)
    } yield err

  private def success[A](a: A, tree: LogTree): DescribedComputation[A] =
    eitherWriter.right(a) :++>> (_ ⇒ tree)

  def failureLog[A](dc: DescribedComputation[A]): DescribedComputation[A] = {
    val logTree = dc.run.written match {
      case NilTree ⇒ NilTree
      case TreeNode(UndescribedLogTreeLabel(s, r), c) ⇒ TreeNode(UndescribedLogTreeLabel(false, r), c)
      case TreeNode(DescribedLogTreeLabel(d, s, r), c) ⇒ TreeNode(DescribedLogTreeLabel(d, false, r), c)
    }
    dc.run.value match {
      case -\/(des) ⇒ failure(des, logTree)
      case \/-(a) ⇒ success(a, logTree)
    }
  }

  def failure[A](description: String): DescribedComputation[A] =
    failure(description, TreeNode(DescribedLogTreeLabel(description, false, referencesMonoid.zero)))

  def success[A](a: A, description: String): DescribedComputation[A] =
    success(a, TreeNode(DescribedLogTreeLabel(description, true, referencesMonoid.zero)))

  implicit class ReferencesSyntax[A](w: DescribedComputation[A]) {
    def ~~(references: ⇒ R) = {
      val newTree = w.run.written match {
        case NilTree ⇒ NilTree
        case TreeNode(l: DescribedLogTreeLabel[R], c) ⇒ TreeNode(l.copy(references = referencesMonoid.append(l.references, references)), c)
        case TreeNode(l: UndescribedLogTreeLabel[R], c) ⇒ TreeNode(l.copy(references = referencesMonoid.append(l.references, references)), c)
      }

      w.run.value match {
        case -\/(error) ⇒ failure(error, newTree)
        case \/-(value) ⇒ success(value, newTree)
      }
    }

    def refersTo(references: ⇒ R) = ~~(references)
  }

  implicit class BooleanSyntax[A](b: Boolean) {
    def ~>?(description: String): DescribedComputation[Boolean] =
      ~>?(description, description)

    def ~>?(failureDescription: ⇒ String, successDescription: ⇒ String): DescribedComputation[Boolean] =
      if (b) success(true, successDescription) else failure(failureDescription)
  }

  implicit class OptionSyntax[A](option: Option[A]) {
    def ~>?(description: String): DescribedComputation[A] = ~>?(description, description)

    def ~>?(noneDescription: ⇒ String, someDescription: ⇒ String): DescribedComputation[A] =
      ~>?(noneDescription, _ ⇒ someDescription)

    def ~>?(noneDescription: ⇒ String, someDescription: A ⇒ String): DescribedComputation[A] =
      option map { a ⇒ success(a, someDescription(a)) } getOrElse failure(noneDescription)

    def ~>|[B](f: A ⇒ DescribedComputation[B], g: ⇒ DescribedComputation[Option[B]]): DescribedComputation[Option[B]] =
      option.map(f).map((v: DescribedComputation[B]) ⇒ v.map(w ⇒ Some(w))) getOrElse g
  }

  implicit class EitherSyntax[A](either: \/[String, A]) {
    def ~>?(leftDescription: String ⇒ String, rightDescription: ⇒ String): DescribedComputation[A] =
      ~>?(leftDescription, _ ⇒ rightDescription)

    def ~>?(description: String): DescribedComputation[A] =
      ~>?((error: String) ⇒ s"$description - $error", description)

    def ~>?(leftDescription: String ⇒ String, rightDescription: A ⇒ String): DescribedComputation[A] =
      either.fold(error ⇒ failure(leftDescription(error)), a ⇒ success(a, rightDescription(a)))
  }

  implicit def iterableSyntax(description: String) = new {
    def ~<[A](mapped: List[DescribedComputation[A]]): DescribedComputation[List[A]] = {
      val parts = mapped.map(m ⇒ (m.run.value, m.run.written)).toList

      val children = parts.map(_._2)
      val branch = TreeNode(
        DescribedLogTreeLabel(
          description,
          allSuccessful(children),
          children.foldLeft(referencesMonoid.zero)((acc, child) ⇒ referencesMonoid.append(acc, references(child)))),
        parts.map(_._2))

      if (mapped.exists(_.run.value.isLeft))
        failure(description, branch)
      else
        success(parts.flatMap(_._1.toOption), branch)
    }
  }

  implicit class ListSyntax[A](as: List[A]) {
    def ~>*[B](description: String, f: A ⇒ DescribedComputation[B]): DescribedComputation[List[B]] = description ~< as.map(f)
  }

  implicit class LeafSyntax[A](a: A) {
    def ~>(description: String): DescribedComputation[A] = success(a, description)
    def ~>(description: A ⇒ String): DescribedComputation[A] = ~>(description(a))
    def ~>!(description: String): DescribedComputation[A] = failure(description)
    def ~>!(description: A ⇒ String): DescribedComputation[A] = ~>!(description(a))
  }

  private def branchHoister(tree: LogTree, description: String, forceSuccess: Boolean = false): LogTree = tree match {
    case NilTree ⇒ TreeNode(DescribedLogTreeLabel(description, true, referencesMonoid.zero))
    case TreeNode(l: UndescribedLogTreeLabel[R], children) ⇒ TreeNode(DescribedLogTreeLabel(description, forceSuccess || allSuccessful(children), l.references), children)
    case TreeNode(l: DescribedLogTreeLabel[R], children) ⇒ TreeNode(DescribedLogTreeLabel(description, forceSuccess || allSuccessful(List(tree)), l.references), List(tree))
  }

  private def references(t: LogTree) = t match {
    case NilTree ⇒ referencesMonoid.zero
    case TreeNode(l: LogTreeLabel[R], _) ⇒ l.references
  }

  private def allSuccessful(trees: Iterable[LogTree]) =
    trees.forall {
      _ match {
        case NilTree ⇒ true
        case TreeNode(l, _) ⇒ l.success
      }
    }

  implicit def branchSyntax(description: String) = new {
    def ~<[A](ew: DescribedComputation[A]): DescribedComputation[A] =
      ew.run.value match {
        case -\/(error) ⇒ failure(error, branchHoister(ew.run.written, description))
        case \/-(value) ⇒ success(value, branchHoister(ew.run.written, description))
      }
  }

  implicit class LabellingSyntax[A](w: DescribedComputation[A]) {
    def ~>(description: String) = description ~< w
  }

  implicit object LogTreeShow extends Show[LogTree] {
    override def shows(t: LogTree) = toList(t).map(line ⇒ "  " * line._1 + line._2).mkString(System.getProperty("line.separator"))

    private def toList(tree: LogTree, depth: Int = 0): List[(Int, String)] =
      tree match {
        case NilTree ⇒ List((depth, "NilTree"))
        case TreeNode(label, children) ⇒ line(depth, label) :: children.flatMap(toList(_, depth + 1))
      }

    private def line(depth: Int, label: LogTreeLabel[R]) =
      (depth, (if (label.success) "" else "Failed: ") + label.fold(l ⇒ l.description, l ⇒ "No Description"))
  }
}
