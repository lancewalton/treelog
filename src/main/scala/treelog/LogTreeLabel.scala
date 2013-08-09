package treelog

sealed trait LogTreeLabel {
  def success(): Boolean
  def fold[T](f: DescribedLogTreeLabel ⇒ T, g: UndescribedLogTreeLabel ⇒ T): T
}

case class DescribedLogTreeLabel(description: String, success: Boolean) extends LogTreeLabel {
  def fold[T](f: DescribedLogTreeLabel ⇒ T, g: UndescribedLogTreeLabel ⇒ T) = f(this)
}

case class UndescribedLogTreeLabel(success: Boolean) extends LogTreeLabel {
  def fold[T](f: DescribedLogTreeLabel ⇒ T, g: UndescribedLogTreeLabel ⇒ T) = g(this)
}