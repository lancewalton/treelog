package treelog

import cats.Show

object LogTreeSyntaxWithoutAnnotations extends LogTreeSyntax[Nothing] {
  implicit object NothingShow extends Show[Nothing] {
    override def show(n: Nothing): String = ""
  }
}