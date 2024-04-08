package treelog

import scala.annotation.nowarn

private[treelog] object ScalaCompat:

  @nowarn
  implicit class LazyListCompanionOps(private val lc: LazyList.type):
    def apply[T](xs: T*): LazyList[T] = xs.toLazyList

  implicit class IterableOps[T](private val iterable: Iterable[T]) extends AnyVal:
    def toLazyList: LazyList[T] = iterable.to(LazyList)
