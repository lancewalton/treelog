package treelog

private[treelog] object ScalaCompat {

  type LazyList[+T] = Stream[T]

  object LazyList {

    val #::  = Stream.#::
    val cons = Stream.cons

    def empty[T]: LazyList[T]         = Stream.empty[T]
    def apply[T](xs: T*): LazyList[T] = xs.toLazyList
  }

  implicit class IterableOps[T](private val iterable: Iterable[T]) extends AnyVal {
    def toLazyList: LazyList[T] = iterable.to[LazyList]
  }

}