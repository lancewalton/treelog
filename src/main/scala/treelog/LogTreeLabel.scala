package treelog

sealed trait LogTreeLabel[Annotation] {
  def success(): Boolean
  def fold[T](f: DescribedLogTreeLabel[Annotation] ⇒ T, g: UndescribedLogTreeLabel[Annotation] ⇒ T): T
  def annotations: Set[Annotation]
}

case class DescribedLogTreeLabel[Annotation](description: String, success: Boolean, annotations: Set[Annotation] = Set[Annotation]()) extends LogTreeLabel[Annotation] {
  def fold[T](f: DescribedLogTreeLabel[Annotation] ⇒ T, g: UndescribedLogTreeLabel[Annotation] ⇒ T) = f(this)
}

case class UndescribedLogTreeLabel[Annotation](success: Boolean, annotations: Set[Annotation] = Set[Annotation]()) extends LogTreeLabel[Annotation] {
  def fold[T](f: DescribedLogTreeLabel[Annotation] ⇒ T, g: UndescribedLogTreeLabel[Annotation] ⇒ T) = g(this)
}