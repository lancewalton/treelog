import treelog.LogTreeSyntax
import scalaz._
import Scalaz._

object LoggingTreeMain extends App with LogTreeSyntax {

  case class Parameters(a: Double, b: Double, c: Double)

  // Roots are real
  println(root(Parameters(2, 5, 3)).run.written.shows)

  /*
Extracting root
  Calculating Numerator
    Calculating Determinant
      Calculating b^2
        Got b: 5.0
        Got b^2: 25.0
      Calculating 4ac
        Got a: 2.0
        Got c: 3.0
        Got 4ac: 24.0
      Got b^2 - 4ac: 1.0
    Calculating sqrt(determinant)
      Determinant (1.0) is >= 0
      Got sqrt(determinant): 1.0
    Got b: 5.0
    Got -b: -5.0
    Got -b + sqrt(determinant): -4.0
  Calculating Denominator
    Got a: 2.0
    Got 2a: 4.0
  Got root = numerator / denominator: -1.0
*/

  // Roots are complex
  println(root(Parameters(2, 5, 10)).run.written.shows)
  /*
Extracting root: Failed
  Calculating Numerator: Failed
    Calculating Determinant
      Calculating b^2
        Got b: 5.0
        Got b^2: 25.0
      Calculating 4ac
        Got a: 2.0
        Got c: 10.0
        Got 4ac: 80.0
      Got b^2 - 4ac: -55.0
    Calculating sqrt(determinant): Failed
      Determinant (-55.0) is < 0: Failed
*/

  private def root(parameters: Parameters) = {
    "Extracting root" ~< {
      for {
        num ← numerator(parameters) ~> "Calculating Numerator"
        den ← denominator(parameters) ~> "Calculating Denominator"
        root ← (num / den) ~> ("Got root = numerator / denominator: " + _)
      } yield root
    }
  }

  private def numerator(parameters: Parameters) =
    for {
      det ← determinant(parameters)
      sqrtDet ← sqrtDeterminant(det)
      b ← parameters.b ~> ("Got b: " + _)
      minusB ← -b ~> ("Got -b: " + _)
      sum ← (minusB + sqrtDet) ~> ("Got -b + sqrt(determinant): " + _)
    } yield sum

  private def sqrtDeterminant(det: Double) =
    "Calculating sqrt(determinant)" ~< {
      for {
        _ ← if (det >= 0) (det ~> (d ⇒ s"Determinant ($d) is >= 0")) else (det ~>! (d ⇒ s"Determinant ($d) is < 0"))
        sqrtDet ← Math.sqrt(det) ~> ("Got sqrt(determinant): " + _)
      } yield sqrtDet
    }

  private def denominator(parameters: Parameters) =
    for {
      a ← parameters.a ~> ("Got a: " + _)
      twoA ← (2 * a) ~> ("Got 2a: " + _)
    } yield twoA

  private def determinant(parameters: Parameters) =
    "Calculating Determinant" ~< {
      for {
        bSquared ← bSquared(parameters)
        fourac ← fourac(parameters)
        determinant ← (bSquared - fourac) ~> ("Got b^2 - 4ac: " + _)
      } yield determinant
    }

  private def bSquared(parameters: Parameters) =
    "Calculating b^2" ~< {
      for {
        b ← parameters.b ~> ("Got b: " + _)
        bSquared ← (b * b) ~> ("Got b^2: " + _)
      } yield bSquared
    }

  private def fourac(parameters: Parameters) =
    "Calculating 4ac" ~< {
      for {
        a ← parameters.a ~> ("Got a: " + _)
        c ← parameters.c ~> ("Got c: " + _)
        fourac ← (4 * a * c) ~> ("Got 4ac: " + _)
      } yield fourac
    }
}