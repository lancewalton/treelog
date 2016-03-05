package treelog

import scalaz.Show

object LogTreeSyntaxWithoutAnnotations extends LogTreeSyntax[Nothing] {
  implicit object NothingShow extends Show[Nothing] {
    override def shows(n: Nothing): String = ""
  }
}