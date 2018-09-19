package treelog

import scala.language.higherKinds
import scalaz.Scalaz._
import scalaz.{-\/, Functor, Monad, Show, \/-, _}

object LogTreeSyntaxWithoutAnnotations extends LogTreeSyntax[Nothing] {

  self: LogTreeSyntax[Nothing] =>

  implicit object NothingShow extends Show[Nothing] {
    override def shows(n: Nothing): String = ""
  }

  /**
    * Represents a computation of type `F[DescribedCompution[A]]`.
    *
    * Example:
    * {{{
    * val x: Option[DescribedComputation[Int]] = Some(1 ~> "1")
    * val y: Option[DescribedComputation[Int]] = Some(2 ~> "1")
    * val z: Option[DescribedComputation[Int]] = (for {
    *   one <- DescribedComputationT(x)
    *   two <- DescribedComputationT(y)
    *   res <- DescribedComputationT(Some((one + two) ~> ("1 + 2 =" + _))
    * } yield res).run
    * }}}
    **/
  case class DescribedComputationT[F[_], A](run: F[DescribedComputation[A]]) {
    self =>

    def map[B](f: A => B)(implicit F: Functor[F]): DescribedComputationT[F, B] = DescribedComputationT(F.map(self.run)(_ map f))

    def mapT[G[_], B](f: F[DescribedComputation[A]] => G[DescribedComputation[B]]): DescribedComputationT[G, B] = DescribedComputationT(f(self.run))

    def flatMap[B](f: A => DescribedComputationT[F, B])(implicit F: Monad[F]): DescribedComputationT[F, B]
    = {

      val v: F[DescribedComputation[B]] = F.bind(self.run) { dcA =>
        // we can extract computation result from dcA because Writer contains "Id[?]".
        dcA.run.value match {
          case \/-(a) =>
            // if dcA succeeded,
            for {
              dcB <- f(a).run // compute another described computation returning B
            } yield {
              // we can concatenate computation history like this.
              for {
                _ <- dcA // we can drop returned value because we just need to concatenate descriptions.
                b <- dcB // extracting final result of type B
              } yield b // final result of this described computation should be B
            }
          case -\/(_) =>
            // if previous computation failed, we just return the history.
            // but we need to convert type of return value into B.
            run.map(_.rightMap(_.asInstanceOf[B]))
        }
      }
      // need to wrap DescribedComputation finally
      DescribedComputationT(v)
    }
  }

  // instances for DescribedComputationT
  private trait DescribedComputationTMonad[F[_]] extends Monad[DescribedComputationT[F, ?]] {
    implicit def F: Monad[F]

    override def bind[A, B](fa: DescribedComputationT[F, A])(f: (A) => DescribedComputationT[F, B]): DescribedComputationT[F, B]
    = fa.flatMap(f)

    override def point[A](a: => A): DescribedComputationT[F, A] = DescribedComputationT(F.pure(success(a)))
  }

  implicit def describedComputationTMonad[F[_]](implicit F0: Monad[F]): Monad[DescribedComputationT[F, ?]] = new DescribedComputationTMonad[F] {
    implicit def F: Monad[F] = F0
  }

  private trait DescribedComputationTHoist extends Hoist[DescribedComputationT] {
    override def hoist[M[_] : Monad, N[_]](f: M ~> N)
    = λ[DescribedComputationT[M, ?] ~> DescribedComputationT[N, ?]](_ mapT f)

    def liftM[G[_], A](a: G[A])(implicit G: Monad[G]): DescribedComputationT[G, A]
    = DescribedComputationT(G.map[A, DescribedComputation[A]](a)((a: A) => success(a)))

    implicit def apply[G[_] : Monad]: Monad[DescribedComputationT[G, ?]]
    = describedComputationTMonad[G]
  }

  implicit val dcTrans: Hoist[DescribedComputationT] = new DescribedComputationTHoist {}

  implicit def describedComputationTEqual[F[_] : Monad, A](implicit F0: Equal[F[DescribedComputation[A]]]): Equal[DescribedComputationT[F, A]] =
    F0.contramap((_: DescribedComputationT[F, A]).run)


  /**
    * This is just a small helper methods to construct DescribedComputationT for contextual f values.
    * Example:
    * {{{
    * import scalaz.effect.IO._
    * import IO._
    * for {
    *   line1 <- readLn ~> ("read input1: " + _)
    *   line2 <- readLn ~> ("read input2: " + _)
    *   res   <- putStr(line1 + line2) ~> ("output input1+input2: " + _)
    * } return res
    * }}}
    *
    * If you want to handle error, you can have several ways.
    * 1.  Id F is an instance of MonadError class, you can call recover error and call ~> like this
    * {{{
    * monadErrorValue.handleError( err => ..return normal computation..) ~> description
    * }}}
    *
    * 2. Or, you can inspect value via match statement and construct described computation like this.
    * {{{
    * f match {
    *   case v if v.isSuccess => DescribedComputationT(v.map(_.logSuccess(description))
    *   case v if v.isFailure => DescribedComputationT(v.map(_.logFailure(description))
    * }
    * }}}
    */
  implicit class DescribedComputationTSyntax[F[_], A](fa: F[A]) {

    def success()(implicit F: Functor[F]): DescribedComputationT[F, A] = DescribedComputationT(fa.map(self.success(_)))
    def logSuccess(description: A => String)(implicit F: Functor[F]): DescribedComputationT[F, A] = DescribedComputationT(fa.map(_.logSuccess(description)))
    def ~>(description: String)(implicit F: Functor[F]): DescribedComputationT[F, A] = ~>(_ => description)
    def ~>(description: A => String)(implicit F: Functor[F]): DescribedComputationT[F, A] = logSuccess(description)

    def logFailure(description: A => String)(implicit F: Functor[F]): DescribedComputationT[F, A] = DescribedComputationT(fa.map(_.logFailure(description)))
    def ~>!(description: A => String)(implicit F: Functor[F]): DescribedComputationT[F, A] = logFailure(description)
    def ~>!(description: String)(implicit F: Functor[F]): DescribedComputationT[F, A] = logFailure(_ => description)
  }


}