package treelog

sealed trait Tree[+Label] {
  def map[A](f: Label ⇒ A): Tree[A]
}
case object NilTree extends Tree[Nothing] {
  def map[A](f: Nothing ⇒ A): Tree[A] = NilTree
}

case class TreeNode[Label](label: Label, children: List[Tree[Label]] = Nil) extends Tree[Label] {
  def map[A](f: Label ⇒ A): Tree[A] = TreeNode(f(label), children.map(_.map(f)))
}