import treelog.LogTreeSyntaxWithoutAnnotations._
import cats.implicits._

object OptionsAndEithersExample extends App {
  val simple = "Calculating sum" ~< {
    for {
      x <- 11 ~> ("x = " + _)
      y <- 2 ~> ("y = " + _)
      sum <- (x + y) ~> ("Sum is " + _)
    } yield sum
  }
  println(simple.value.written.show)

  val options = "Calculating option sum" ~< {
    for {
      x <- 11.some ~>? ("No x", "x = " + _)
      y <- 2.some ~>? ("No y", "y = " + _)
      sum <- (x + y) ~> (v => "Sum is " + v)
    } yield sum
  }

  val noOptions = "Calculating no option sum" ~< {
    for {
      x <- 11.some ~>? ("No x", "x = " + _)
      y <- none[Int] ~>? ("No y", "y = " + _)
      sum <- (x + y) ~> (v => "Sum is " + v)
    } yield sum
  }

  println(noOptions.value.written.show)

  val eithers = "Calculating either sum" ~< {
    for {
      x <- 11.asRight[String] ~>? ("x = " + _)
      y <- 2.asRight[String] ~>? ("y = " + _)
      sum <- (x + y) ~> (v => "Sum is " + v)
    } yield sum
  }

  println(eithers.value.written.show)

  val leftEithers: DescribedComputation[Int] = "Calculating left either sum" ~< {
    for {
      x <- 11.asRight[String] ~>? ("x = " + _)
      y <- "fubar".asLeft[Int] ~>? ("y = " + _)
      sum <- (x + y) ~> (v => "Sum is " + v)
    } yield sum
  }

  val leftEitherWriter: LogTreeWriter[Either[String, Int]] = leftEithers.value
  println(leftEitherWriter.written.show)

  leftEitherWriter.value match {
    case Right(sucessValue) => println(s"Success: $sucessValue")
    case Left(failureValue) => println(s"Failure: $failureValue")
  }

}