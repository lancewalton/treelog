package treelog

import scalaz.{ -\/, \/-, \/, EitherT, Monoid, Show, Writer, idInstance }
import scalaz.syntax.monadListen._
import scala.annotation.tailrec

trait LogTreeSyntax[Annotation] {
  type LogTree = Tree[LogTreeLabel[Annotation]]
  type LogTreeWriter[+V] = Writer[LogTree, V]
  type DescribedComputation[+V] = EitherT[LogTreeWriter, String, V]

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

  private def failure[A](description: String, tree: LogTree): DescribedComputation[A] =
    for {
      _ ← eitherWriter.tell(tree)
      err ← eitherWriter.left(description)
    } yield err

  private def success[A](a: A, tree: LogTree): DescribedComputation[A] = eitherWriter.right(a) :++>> (_ ⇒ tree)

  def failureLog[A](dc: DescribedComputation[A]): DescribedComputation[A] = {
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

  def failure[A](description: String): DescribedComputation[A] = failure(description, TreeNode(DescribedLogTreeLabel(description, false)))

  def success[A](a: A, description: String): DescribedComputation[A] =
    success(a, TreeNode(DescribedLogTreeLabel(description, true, Set[Annotation]())))

  implicit class AnnotationsSyntax[A](w: DescribedComputation[A]) {
    def ~~(annotations: Set[Annotation]): DescribedComputation[A] = {
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

    def ~~(annotation: Annotation): DescribedComputation[A] = ~~(Set(annotation))

    def annotateWith(annotations: Set[Annotation]): DescribedComputation[A] = ~~(annotations)
    def annotateWith(annotation: Annotation): DescribedComputation[A] = ~~(annotation)

    def allAnnotations = {
      def recurse(tree: LogTree, accumulator: Set[Annotation]): Set[Annotation] = {
        tree match {
          case NilTree ⇒ accumulator
          case t: TreeNode[LogTreeLabel[Annotation]] ⇒ t.children.foldLeft(accumulator ++ t.label.annotations)((acc, child) ⇒ recurse(child, acc))
        }
      }
      recurse(w.run.written, Set())
    }
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

    def ~>?(description: A ⇒ String): DescribedComputation[A] =
      ~>?((error: String) ⇒ error, description)

    def ~>?(leftDescription: String ⇒ String, rightDescription: A ⇒ String): DescribedComputation[A] =
      either.fold(error ⇒ failure(leftDescription(error)), a ⇒ success(a, rightDescription(a)))
  }

  implicit class IterableSyntax(description: String) {
    def ~<[A](mapped: List[DescribedComputation[A]]): DescribedComputation[List[A]] = {
      val parts = mapped.map(m ⇒ (m.run.value, m.run.written)).toList

      val children = parts.map(_._2)
      val branch = TreeNode(
        DescribedLogTreeLabel(
          description,
          allSuccessful(children),
          Set[Annotation]()),
        parts.map(_._2))

      if (mapped.exists(_.run.value.isLeft)) failure(description, branch) else success(parts.flatMap(_._1.toOption), branch)
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

    private def branchHoister(tree: LogTree, description: String, forceSuccess: Boolean = false): LogTree = tree match {
      case NilTree ⇒ TreeNode(DescribedLogTreeLabel(description, true))
      case TreeNode(l: UndescribedLogTreeLabel[Annotation], children) ⇒ TreeNode(DescribedLogTreeLabel(description, forceSuccess || allSuccessful(children)), children)
      case TreeNode(l: DescribedLogTreeLabel[Annotation], children) ⇒ TreeNode(DescribedLogTreeLabel(description, forceSuccess || allSuccessful(List(tree))), List(tree))
    }
  }

  implicit class LabellingSyntax[A](w: DescribedComputation[A]) {
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