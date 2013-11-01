import treelog.LogTreeSyntaxWithoutAnnotations._
import scalaz._
import Scalaz._

object OptionsAndEithersExample extends App {
  val simple = "Calculating sum" ~< {
    for {
      x ← 11 ~> ("x = " + _)
      y ← 2 ~> ("y = " + _)
      sum ← (x + y) ~> ("Sum is " + _)
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

  val leftEithers: DescribedComputation[Int] = "Calculating left either sum" ~< {
    for {
      x ← 11.right[String] ~>? ("x = " + _)
      y ← "fubar".left[Int] ~>? ("y = " + _)
      sum ← (x + y) ~> (v ⇒ "Sum is " + v)
    } yield sum
  }

  val leftEitherWriter: LogTreeWriter[\/[String, Int]] = leftEithers.run
  println(leftEitherWriter.written.shows)

  leftEitherWriter.value match {
    case \/-(sucessValue) => println(s"Success: $sucessValue")
    case -\/(failureValue) => println(s"Failure: $failureValue")
  }

}