package treelog

sealed trait Tree[+Label]
case object NilTree extends Tree[Nothing]
case class TreeNode[Label](label: Label, children: List[Tree[Label]] = Nil) extends Tree[Label]