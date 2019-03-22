package treelog

import cats.{Eval, Foldable, Now}
import cats.kernel.Order
import cats.kernel.instances.StaticMethods

private[treelog] object ScalaCompat {

  implicit def orderForLazyList[A: Order]: Order[LazyList[A]] = new Order[LazyList[A]] {
    def compare(xs: LazyList[A], ys: LazyList[A]): Int =
      if (xs eq ys) 0 else StaticMethods.iteratorCompare(xs.iterator, ys.iterator)
  }

  implicit val foldableForLazyList: Foldable[LazyList] = new Foldable[LazyList] {
    def foldLeft[A, B](fa: LazyList[A], b: B)(f: (B, A) => B): B = fa.foldLeft(b)(f)
    def foldRight[A, B](fa: LazyList[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] =
      Now(fa).flatMap(s => if (s.isEmpty) lb else f(s.head, Eval.defer(foldRight(s.tail, lb)(f))))
  }

  implicit class LazyListCompanionOps(private val lc: LazyList.type) {
    def apply[T](xs: T*): LazyList[T] = xs.toLazyList
  }

  implicit class IterableOps[T](private val iterable: Iterable[T]) extends AnyVal {
    def toLazyList: LazyList[T] = iterable.to(LazyList)
  }

}