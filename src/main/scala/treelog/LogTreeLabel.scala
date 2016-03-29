package treelog

import scalaz.Equal

sealed trait LogTreeLabel[Annotation] extends Product with Serializable {
  def success(): Boolean
  def fold[T](f: DescribedLogTreeLabel[Annotation] ⇒ T, g: UndescribedLogTreeLabel[Annotation] ⇒ T): T
  def annotations: Set[Annotation]
}

final case class DescribedLogTreeLabel[Annotation](description: String, success: Boolean, annotations: Set[Annotation] = Set[Annotation]()) extends LogTreeLabel[Annotation] {
  def fold[T](f: DescribedLogTreeLabel[Annotation] ⇒ T, g: UndescribedLogTreeLabel[Annotation] ⇒ T) = f(this)
}

final case class UndescribedLogTreeLabel[Annotation](success: Boolean, annotations: Set[Annotation] = Set[Annotation]()) extends LogTreeLabel[Annotation] {
  def fold[T](f: DescribedLogTreeLabel[Annotation] ⇒ T, g: UndescribedLogTreeLabel[Annotation] ⇒ T) = g(this)
}

object LogTreeLabel {
  implicit def LogTreeLabelEqual[A]: Equal[LogTreeLabel[A]] = new Equal[LogTreeLabel[A]] {
    def equal(a1: LogTreeLabel[A], a2: LogTreeLabel[A]): Boolean = a1 == a2
  }
}
