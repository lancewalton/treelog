package treelog

import org.scalatest._
import scalaz.{ -\/, \/- }
import scalaz.Monoid

object TestLogTreeSyntax extends LogTreeSyntax[Set[Int]] {
  val referencesMonoid = new Monoid[Set[Int]] {
    def zero = Set()
    def append(a: Set[Int], b: ⇒ Set[Int]) = a ++ b
  }
}

class LogTreeSyntaxSpec extends Spec with MustMatchers {
  import TestLogTreeSyntax._

  object `success must` {
    def `produce a value with the given value on the right` { success(1, "Yay").run.value must be === \/-(1) }
    def `produce a written with a success leaf node and the given desscription` { success(1, "Yay").run.written must be === node("Yay", true) }
  }

  object `failure must` {
    def `produce a value with the given message on the left` { failure("Boo").run.value must be === -\/("Boo") }
    def `produce a written with a failure leaf node and the given desscription` { failure("Boo").run.written must be === node("Boo", false) }
  }

  object `leaf creation with ~> and a string must` {
    def `do the same as 'success'` { (1 ~> "Foo").run.run must be === success(1, "Foo").run.run }
  }

  object `leaf creation with ~> and a function must` {
    def `do the same as 'success' after applying the function to the value` { (1 ~> (x ⇒ s"Foo: $x")).run.run must be === success(1, "Foo: 1").run.run }
  }

  object `leaf creation with ~>! and a string must` {
    def `do the same as 'failure'` { (1 ~>! "Foo").run.run must be === failure("Foo").run.run }
  }

  object `leaf creation with ~>! and a function must` {
    def `do the same as 'failure' after applying the function to the value` { (1 ~>! (x ⇒ s"Foo: $x")).run.run must be === failure("Foo: 1").run.run }
  }

  object `boolean ~>? with a description must` {
    def `do the same as 'success' when the boolean is true` { (true ~>? "Foo").run.run must be === success(true, "Foo").run.run }
    def `do the same as 'failure' when the boolean is false` { (false ~>? "Foo").run.run must be === failure("Foo").run.run }
  }

  object `boolean ~>? with a failureDescription and a successDescription must` {
    def `do the same as 'success' with the successDescription when the boolean is true` { (true ~>? ("Foo", "Bar")).run.run must be === success(true, "Bar").run.run }
    def `do the same as 'failure' with the failureDescription when the boolean is false` { (false ~>? ("Foo", "Bar")).run.run must be === failure("Foo").run.run }
  }

  object `Option ~>? with a description must` {
    def `do the same as 'success' when the Option is Some` { (Some(2) ~>? "Foo").run.run must be === success(2, "Foo").run.run }
    def `do the same as 'failure' when the Option is None` { (None ~>? "Foo").run.run must be === failure("Foo").run.run }
  }

  object `Option ~>? with a noneDescription and a someDescription must` {
    def `do the same as 'success' when the Option is Some` { (Some(2) ~>? ("Foo", "Bar")).run.run must be === success(2, "Bar").run.run }
    def `do the same as 'failure' when the Option is None` { (None ~>? ("Foo", "Bar")).run.run must be === failure("Foo").run.run }
  }

  object `Option ~>? with a noneDescription and a function for someDescription must` {
    def `do the same as 'success' when the Option is Some` { (Some(2) ~>? ("Foo", x ⇒ "Bar: " + x)).run.run must be === success(2, "Bar: 2").run.run }
    def `do the same as 'failure' when the Option is None` { (None ~>? ("Foo", x ⇒ "Bar: " + x)).run.run must be === failure("Foo").run.run }
  }

  object `\\/ ~>? with a description must` {
    def `do the same as 'success' with right` { (\/-(2) ~>? "Foo").run.run must be === success(2, "Foo").run.run }
    def `do the same as 'failure' with left` { (-\/("Fail") ~>? "Foo").run.run must be === failure("Foo - Fail").run.run }
  }

  object `\\/ ~>? with a leftDescription function and a rightDescription must` {
    def `do the same as 'success' with right` { (\/-(2) ~>? (x ⇒ "Foo: " + x, "Bar")).run.run must be === success(2, "Bar").run.run }
    def `do the same as 'failure' with left` { (-\/("Fail") ~>? (x ⇒ "Foo: " + x, "Bar")).run.run must be === failure("Foo: Fail").run.run }
  }

  object `\\/ ~>? with a leftDescription function and a rightDescription function must` {
    def `do the same as 'success' with right` { (\/-(2) ~>? (x ⇒ "Foo: " + x, x ⇒ "Bar: " + x)).run.run must be === success(2, "Bar: 2").run.run }
    def `do the same as 'failure' with left` { (-\/("Fail") ~>? (x ⇒ "Foo: " + x, x ⇒ "Bar: " + x)).run.run must be === failure("Foo: Fail").run.run }
  }

  object `~>* must` {
    def `return a success when all children are successes` {
      val result = List(1, 2) ~>* ("Parent", x ⇒ success(3 * x, "Child: " + x))
      result.run.written must be === node("Parent", true, node("Child: 1", true), node("Child: 2", true))
      result.run.value must be === \/-(List(3, 6))
    }

    def `return a failure when one or more children is a failure` {
      val result = List(1, 2) ~>* ("Parent", x ⇒ (x == 1) ~>? s"Child: $x")
      result.run.written must be === node("Parent", false, node("Child: 1", true), node("Child: 2", false))
      result.run.value must be === -\/("Parent")
    }
  }

  object `Hoisting a leaf into a branch must` {
    def `when the leaf is a success, create a success root node with the description with a single child which is the leaf` {
      val result =
        "Parent" ~< {
          for (x ← 1 ~> "Child") yield x
        }
      result.run.written must be === node("Parent", true, node("Child", true))
      result.run.value must be === \/-(1)
    }

    def `when the leaf is a failure, create a failure root node with the description with a single child which is the leaf` {
      val result =
        "Parent" ~< {
          for (x ← failure("Child")) yield x
        }
      result.run.written must be === node("Parent", false, node("Child", false))
      result.run.value must be === -\/("Child")
    }
  }

  object `Combining two leaves using a for comprehension must` {
    def `create a branch with the yielded value, without a description and with success equal to true when both leaves have success = true` {
      val result = for {
        x ← 1 ~> "One"
        y ← 2 ~> "Two"
      } yield x + y
      result.run.value must be === \/-(3)
      result.run.written must be === node(true, node("One", true), node("Two", true))
    }

    def `create a branch with the description of the first failed leaf on the left, without a description and with success equal to false when at least one of the leaves has success = false` {
      val result = for {
        x ← 1 ~> "One"
        y ← 2 ~>! "Two"
      } yield x + y
      result.run.value must be === -\/("Two")
      result.run.written must be === node(false, node("One", true), node("Two", false))
    }
  }

  object `Hoisting a branch must` {
    def `copy the existing branch and give it the description when the hoisted branch has no description` {
      val result = "Parent" ~< {
        for {
          x ← 1 ~> "One"
          y ← 2 ~> "Two"
        } yield x + y
      }
      result.run.value must be === \/-(3)
      result.run.written must be === node("Parent", true, node("One", true), node("Two", true))
    }

    def `create a new parent above the existing branch and give it the description when the hoisted branch has a description` {
      val result = "Grandparent" ~< {
        "Parent" ~< {
          for {
            x ← 1 ~> "One"
            y ← 2 ~> "Two"
          } yield x + y
        }
      }
      result.run.value must be === \/-(3)
      result.run.written must be === node("Grandparent", true, node("Parent", true, node("One", true), node("Two", true)))
    }
  }

  private def node(description: String, success: Boolean, children: TreeNode[LogTreeLabel]*) =
    nodeWithData(description, success, children: _*)

  private def node(success: Boolean, children: TreeNode[LogTreeLabel]*) =
    TreeNode(UndescribedLogTreeLabel(success), children.toList)

  private def nodeWithData(description: String, success: Boolean, children: TreeNode[LogTreeLabel]*) =
    TreeNode(DescribedLogTreeLabel(description, success), children.toList)
}