package treelog

import cats.{Functor, Monad, Show}

object LogTreeSyntaxWithoutAnnotations extends LogTreeSyntax[Nothing] {
  implicit object NothingShow extends Show[Nothing] {
    override def show(n: Nothing): String = ""
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

      val v: F[DescribedComputation[B]] = F.flatMap(self.run) { dcA =>
        // we can extract computation result from dcA because Writer contains "Id[?]".
        dcA.value.value match {
          case Right(a) =>
            // if dcA succeeded,
            F.map(f(a).run) {
              dcB ⇒
                // we can concatenate computation history like this.
                for {
                  _ <- dcA // we can drop returned value because we just need to concatenate descriptions.
                  b <- dcB // extracting final result of type B
                } yield b // final result of this described computation should be B
            }
          case Left(_) =>
            // if previous computation failed, we just return the history.
            // but we need to convert type of return value into B.
            F.map(run)(_.asInstanceOf[DescribedComputation[B]])
        }
      }
      // need to wrap DescribedComputation finally
      DescribedComputationT(v)
    }
  }
}