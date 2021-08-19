package treelog

import cats._
import cats.free.Trampoline
import cats.implicits._
import treelog.ScalaCompat._

/** Partially copied from Scalaz. */
sealed abstract class Tree[A] {

  /** The label at the root of this tree. */
  def rootLabel: A

  /** The child nodes of this tree. */
  def subForest: LazyList[Tree[A]]

  /** A 2D String representation of this Tree. */
  def drawTree(implicit sh: Show[A]): String = {
    val reversedLines = draw.run
    val first         = new StringBuilder(reversedLines.head.toString.reverse)
    val rest          = reversedLines.tail
    rest
      .foldLeft(first)((acc, elem) => acc.append("\n").append(elem.toString.reverse))
      .append("\n")
      .toString
  }

  /** A 2D String representation of this Tree, separated into lines. Uses reversed StringBuilders for performance,
    * because they are prepended to.
    */
  private def draw(implicit sh: Show[A]): Trampoline[Vector[StringBuilder]] = {
    import Trampoline._
    val branch = " -+" // "+- ".reverse
    val stem   = " -`" // "`- ".reverse
    val trunk  = "  |" // "|  ".reverse

    def drawSubTrees(s: LazyList[Tree[A]]): Trampoline[Vector[StringBuilder]] =
      s match {
        case ts if ts.isEmpty => done(Vector.empty[StringBuilder])
        case t #:: ts if ts.isEmpty =>
          defer(t.draw).map(subtree => new StringBuilder("|") +: shift(stem, "   ", subtree))
        case t #:: ts =>
          for {
            subtree       <- defer(t.draw)
            otherSubtrees <- defer(drawSubTrees(ts))
          } yield new StringBuilder("|") +: (shift(branch, trunk, subtree) ++ otherSubtrees)
      }

    def shift(first: String, other: String, s: Vector[StringBuilder]): Vector[StringBuilder] = {
      var i = 0
      while (i < s.length) {
        if (i == 0) s(i).append(first)
        else s(i).append(other)
        i += 1
      }
      s
    }

    drawSubTrees(subForest).map(subtrees => new StringBuilder(sh.show(rootLabel).reverse) +: subtrees)
  }

  /** Pre-order traversal. */
  def flatten: Eval[LazyList[A]] = {
    def squish(tree: Tree[A], xs: Eval[LazyList[A]]): Eval[LazyList[A]] =
      Foldable[LazyList].foldRight(tree.subForest, xs)(squish(_, _)).map(v => LazyList.cons(tree.rootLabel, v))

    squish(this, Eval.now(LazyList.empty))
  }

}

sealed abstract class TreeInstances {

  implicit val treeTraverse: Traverse[Tree] = new Traverse[Tree] {

    override def map[A, B](fa: Tree[A])(f: A => B): Tree[B] = fa map f

    override def traverse[G[_]: Applicative, A, B](fa: Tree[A])(f: A => G[B]): G[Tree[B]] = fa.traverse(f)

    override def foldRight[A, B](fa: Tree[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] = fa.foldRight(lb)(f)

    override def foldLeft[A, B](fa: Tree[A], z: B)(f: (B, A) => B): B = fa.flatten.value.foldLeft(z)(f)

    override def foldMap[A, B](fa: Tree[A])(f: A => B)(implicit F: Monoid[B]): B = fa foldMap f
  }

  implicit def treeEqual[A](implicit A0: Eq[A]): Eq[Tree[A]] = new TreeEqual[A] { def A = A0 }

  implicit def treeOrder[A](implicit A0: Order[A]): Order[Tree[A]] =
    new Order[Tree[A]] with TreeEqual[A] {
      def A: Order[A] = A0

      override def compare(x: Tree[A], y: Tree[A]) =
        A.compare(x.rootLabel, y.rootLabel) match {
          case 0 => Order[LazyList[Tree[A]]].compare(x.subForest, y.subForest)
          case i => i
        }

    }

}

object Tree extends TreeInstances {

  /** Node represents a tree node that may have children.
    *
    * You can use Node for tree construction or pattern matching.
    */
  object Node {

    def apply[A](root: => A, forest: => LazyList[Tree[A]]): Tree[A] =
      new Tree[A] {
        def rootLabel = root
        def subForest = forest

        override def toString = "<tree>"
      }

    def unapply[A](t: Tree[A]): Option[(A, LazyList[Tree[A]])] = Some((t.rootLabel, t.subForest))
  }

  /** Leaf represents a tree node with no children.
    *
    * You can use Leaf for tree construction or pattern matching.
    */
  object Leaf {
    def apply[A](root: => A): Tree[A] = Node(root, LazyList.empty)

    def unapply[A](t: Tree[A]): Option[A] =
      t match {
        case Node(root, f) if f.isEmpty => Some(root)
        case _                          => None
      }

  }

}

private trait TreeEqual[A] extends Eq[Tree[A]] {
  def A: Eq[A]

  final override def eqv(a1: Tree[A], a2: Tree[A]): Boolean = {
    def corresponds[B](a1: LazyList[Tree[A]], a2: LazyList[Tree[A]]): Trampoline[Boolean] =
      (a1.isEmpty, a2.isEmpty) match {
        case (true, true)          => Trampoline.done(true)
        case (_, true) | (true, _) => Trampoline.done(false)
        case _ =>
          for {
            heads <- trampolined(a1.head, a2.head)
            tails <- corresponds(a1.tail, a2.tail)
          } yield heads && tails
      }

    def trampolined(a1: Tree[A], a2: Tree[A]): Trampoline[Boolean] =
      for {
        roots      <- Trampoline.done(A.eqv(a1.rootLabel, a2.rootLabel))
        subForests <- corresponds(a1.subForest, a2.subForest)
      } yield roots && subForests

    trampolined(a1, a2).run
  }

}
