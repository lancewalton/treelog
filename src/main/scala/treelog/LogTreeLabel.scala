package treelog

import cats.kernel.Eq

sealed trait LogTreeLabel[Annotation] extends Product with Serializable {
  def success: Boolean
  def fold[T](f: DescribedLogTreeLabel[Annotation] => T, g: UndescribedLogTreeLabel[Annotation] => T): T
  def annotations: Set[Annotation]
  def addAnnotations(a: Set[Annotation]): LogTreeLabel[Annotation]
}

final case class DescribedLogTreeLabel[Annotation](
  description: String,
  success: Boolean,
  annotations: Set[Annotation] = Set[Annotation]()
) extends LogTreeLabel[Annotation] {
  def fold[T](f: DescribedLogTreeLabel[Annotation] => T, g: UndescribedLogTreeLabel[Annotation] => T): T = f(this)
  def addAnnotations(a: Set[Annotation]): LogTreeLabel[Annotation]                                       =
    copy(annotations = annotations ++ a)
}

final case class UndescribedLogTreeLabel[Annotation](success: Boolean, annotations: Set[Annotation] = Set[Annotation]())
    extends LogTreeLabel[Annotation] {
  def fold[T](f: DescribedLogTreeLabel[Annotation] => T, g: UndescribedLogTreeLabel[Annotation] => T): T = g(this)
  def addAnnotations(a: Set[Annotation]): LogTreeLabel[Annotation]                                       =
    copy(annotations = annotations ++ a)
}

object LogTreeLabel {
  implicit def LogTreeLabelEqual[A]: Eq[LogTreeLabel[A]] = Eq.fromUniversalEquals[LogTreeLabel[A]]
}
