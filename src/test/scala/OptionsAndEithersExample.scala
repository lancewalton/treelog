import treelog.LogTreeSyntax

import scalaz._
import Scalaz._

object OptionsAndEithersExample extends App with LogTreeSyntax[Double] {
  val referencesMonoid: Monoid[Double] = implicitly[Monoid[Double]]

  val simple = "Calculating sum" ~< {
    for {
      x ← 11 ~> (v ⇒ s"x = $v")
      y ← 2 ~> (v ⇒ s"y = $v")
      sum ← (x + y) ~> (v ⇒ "Sum is " + v)
    } yield sum
  }
  println(simple.run.written.shows)

  val options = "Calculating option sum" ~< {
    for {
      x ← 11.some ~>? ("No x", "x = " + _)
      y ← 2.some ~>? ("No y", "y = " + _)
      sum ← (x + y) ~> (v ⇒ "Sum is " + v)
    } yield sum
  }

  val noOptions = "Calculating no option sum" ~< {
    for {
      x ← 11.some ~>? ("No x", "x = " + _)
      y ← none[Int] ~>? ("No y", "y = " + _)
      sum ← (x + y) ~> (v ⇒ "Sum is " + v)
    } yield sum
  }

  println(noOptions.run.written.shows)

  val eithers = "Calculating either sum" ~< {
    for {
      x ← 11.right[String] ~>? ("x = " + _)
      y ← 2.right[String] ~>? ("y = " + _)
      sum ← (x + y) ~> (v ⇒ "Sum is " + v)
    } yield sum
  }

  println(eithers.run.written.shows)

  val leftEithers = "Calculating left either sum" ~< {
    for {
      x ← 11.right[String] ~>? ("x = " + _)
      y ← "fubar".left[Int] ~>? ("y = " + _)
      sum ← (x + y) ~> (v ⇒ "Sum is " + v)
    } yield sum
  }

  println(leftEithers.run.written.shows)

}