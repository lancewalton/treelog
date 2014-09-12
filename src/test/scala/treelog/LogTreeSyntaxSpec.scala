package treelog

import org.scalatest._
import scalaz._
import Scalaz._

class LogTreeSyntaxSpec extends Spec with MustMatchers {
  import LogTreeSyntaxWithoutAnnotations._

  // we need this for tests. If you just use -\/("Fail") you end up with a \/[Nothing, String]
  // which upsets compiler when it cannot find an appropriate scalaz.Equal typeclass
  val aFailure: \/[String, String] = -\/("Fail")

  object `success must` {
    def `produce a value with the given value on the right`() = { success(1, "Yay").run.value must be === \/-(1) }
    def `produce a written with a success leaf node and the given desscription`() = { assert(success(1, "Yay").run.written ≟ node("Yay", true)) }
  }

  object `failure must` {
    def `produce a value with the given message on the left`() = { failure("Boo").run.value must be === -\/("Boo") }
    def `produce a written with a failure leaf node and the given desscription`() = { assert(failure("Boo").run.written ≟ node("Boo", false)) }
  }

  object `~~ must` {
    def `add the annotation to the relevant node's annotation set`() = {
      val s = new LogTreeSyntax[Int] {}
      import s._

      val node = success(1, "Yay") ~~ 1 ~~ 2
      assert(node.run.value ≟ \/-(1))
      assert(node.run.written ≟ Tree.leaf(DescribedLogTreeLabel("Yay", true, Set(1, 2))))
    }
  }

  object `allAnnotations must` {
    def `compile the set of all annotations in the tree`() = {
      val s = new LogTreeSyntax[Int] {}
      import s._

      val computation = "Parent" ~< {
        for {
          i ← 1 ~> "Child One" ~~ 1
          j ← 2 ~> "Child Two" ~~ 2
        } yield 3
      } ~~ 3

      assert(computation.allAnnotations == Set(1, 2, 3))
    }
  }

  object `leaf creation with ~> and a string must` {
    def `do the same as 'success'`() = { assert((1 ~> "Foo").run.run ≟ success(1, "Foo").run.run) }
  }

  object `leaf creation with ~> and a function must` {
    def `do the same as 'success' after applying the function to the value`() = { assert((1 ~> (x ⇒ s"Foo: $x")).run.run ≟ success(1, "Foo: 1").run.run) }
  }

  object `leaf creation with ~>! and a string must` {
    def `do the same as 'failure'`() = { assert((1 ~>! "Foo").run.run ≟ failure("Foo").run.run) }
  }

  object `leaf creation with ~>! and a function must` {
    def `do the same as 'failure' after applying the function to the value`() = { assert((1 ~>! (x ⇒ s"Foo: $x")).run.run ≟ failure("Foo: 1").run.run) }
  }

  object `boolean ~>? with a description must` {
    def `do the same as 'success' when the boolean is true`() = { assert((true ~>? "Foo").run.run ≟ success(true, "Foo").run.run) }
    def `do the same as 'failure' when the boolean is false`() = { assert((false ~>? "Foo").run.run ≟ failure("Foo").run.run) }
  }

  object `boolean ~>? with a failureDescription and a successDescription must` {
    def `do the same as 'success' with the successDescription when the boolean is true`() = { assert((true ~>? ("Foo", "Bar")).run.run ≟ success(true, "Bar").run.run) }
    def `do the same as 'failure' with the failureDescription when the boolean is false`() = { assert((false ~>? ("Foo", "Bar")).run.run ≟ failure("Foo").run.run) }
  }

  object `Option ~>? with a description must` {
    def `do the same as 'success' when the Option is Some`() = { assert((Some(2) ~>? "Foo").run.run ≟ success(2, "Foo").run.run) }
    def `do the same as 'failure' when the Option is None`() = { assert((none[Int] ~>? "Foo").run.run ≟ failure("Foo").run.run) }
  }

  object `Option ~>? with a noneDescription and a someDescription must` {
    def `do the same as 'success' when the Option is Some`() = { assert((Some(2) ~>? ("Foo", "Bar")).run.run ≟ success(2, "Bar").run.run) }
    def `do the same as 'failure' when the Option is None`() = { assert((none[Int] ~>? ("Foo", "Bar")).run.run ≟ failure("Foo").run.run) }
  }

  object `Option ~>? with a noneDescription and a function for someDescription must` {
    def `do the same as 'success' when the Option is Some`() = { assert((Some(2) ~>? ("Foo", x ⇒ "Bar: " + x)).run.run ≟ success(2, "Bar: 2").run.run) }
    def `do the same as 'failure' when the Option is None`() = { assert((none[Int] ~>? ("Foo", x ⇒ "Bar: " + x)).run.run ≟ failure("Foo").run.run) }
  }

  object `\\/ ~>? with a description must` {
    def `do the same as 'success' with right`() = { assert((\/-(2) ~>? "Foo").run.run ≟ success(2, "Foo").run.run) }
    def `do the same as 'failure' with left`() = { assert((aFailure ~>? "Foo").run.run ≟ failure("Foo - Fail").run.run) }
  }

  object `\\/ ~>? with a leftDescription function and a rightDescription must` {
    def `do the same as 'success' with right`() = { assert((\/-(2) ~>? (x ⇒ "Foo: " + x, "Bar")).run.run ≟ success(2, "Bar").run.run) }
    def `do the same as 'failure' with left`() = { assert((aFailure ~>? (x ⇒ "Foo: " + x, "Bar")).run.run ≟ failure("Foo: Fail").run.run) }
  }

  object `\\/ ~>? with a leftDescription function and a rightDescription function must` {
    def `do the same as 'success' with right`() = { assert((\/-(2) ~>? (x ⇒ "Foo: " + x, x ⇒ "Bar: " + x)).run.run ≟ success(2, "Bar: 2").run.run) }
    def `do the same as 'failure' with left`() = { assert((aFailure ~>? (x ⇒ "Foo: " + x, x ⇒ "Bar: " + x)).run.run ≟ failure("Foo: Fail").run.run) }
  }

  object `~>* must` {
    def `return a success when all children are successes`() = {
      val result = List(1, 2) ~>* ("Parent", x ⇒ success(3 * x, "Child: " + x))
      assert(result.run.written ≟ node("Parent", true, node("Child: 1", true), node("Child: 2", true)))
      assert(result.run.value ≟ \/-(List(3, 6)))
    }

    def `return a failure when one or more children is a failure`() = {
      val result = List(1, 2) ~>* ("Parent", x ⇒ (x ≟ 1) ~>? s"Child: $x")
      assert(result.run.written ≟ node("Parent", false, node("Child: 1", true), node("Child: 2", false)))
      assert(result.run.value ≟ -\/("Parent"))
    }
  }

  object `~>/ must` {
    def `return success with the folded value  and a log tree describing the fold when all parts are successes`() = {
      val result = List(1, 2, 3) ~>/ ("Foo", 0 ~> "Initial Value", (acc: Int, x: Int) ⇒ (acc + x) ~> (t ⇒ s"x=$x, result=$t"))
      assert(result.run.written ≟
        node("Foo", true,
          node("Initial Value", true),
          node("x=1, result=1", true),
          node("x=2, result=3", true),
          node("x=3, result=6", true)))
      result.run.value must equal(\/-(6))
    }

    def `return failure and a log tree describing the fold as far as it got`() = {
      def thing(acc: Int, x: Int) = if (x == 3) failure[Int]("No") else (acc + x) ~> (t ⇒ s"x=$x, result=$t")
      val result = List(1, 2, 3) ~>/ ("Bar", 0 ~> "Initial Value", thing)
      assert(result.run.written ≟
        node("Bar", false,
          node("Initial Value", true),
          node("x=1, result=1", true),
          node("x=2, result=3", true),
          node("No", false)))
      result.run.value must equal(-\/("Bar"))
    }
  }

  object `Hoisting a leaf into a branch must` {
    def `when the leaf is a success, create a success root node with the description with a single child which is the leaf`() = {
      val result =
        "Parent" ~< {
          for (x ← 1 ~> "Child") yield x
        }
      assert(result.run.written ≟ node("Parent", true, node("Child", true)))
      assert(result.run.value ≟ \/-(1))
    }

    def `when the leaf is a failure, create a failure root node with the description with a single child which is the leaf`() = {
      val result: DescribedComputation[String] =
        "Parent" ~< {
          for (x ← failure[String]("Child")) yield x
        }
      assert(result.run.written ≟ node("Parent", false, node("Child", false)))
      assert(result.run.value ≟ -\/("Parent"))
    }
  }

  object `Combining two leaves using a for comprehension must` {
    def `create a branch with the yielded value, without a description and with success equal to true when both leaves have success = true`() = {
      val result = for {
        x ← 1 ~> "One"
        y ← 2 ~> "Two"
      } yield x + y
      assert(result.run.value ≟ \/-(3))
      assert(result.run.written ≟ node(true, node("One", true), node("Two", true)))
    }

    def `create a branch with the description of the first failed leaf on the left, without a description and with success equal to false when at least one of the leaves has success = false`() = {
      val result = for {
        x ← 1 ~> "One"
        y ← 2 ~>! "Two"
      } yield x + y
      assert(result.run.value ≟ -\/("Two"))
      assert(result.run.written ≟ node(false, node("One", true), node("Two", false)))
    }
  }

  object `Hoisting a branch must` {
    def `copy the existing branch and give it the description when the hoisted branch has no description`() = {
      val result = "Parent" ~< {
        for {
          x ← 1 ~> "One"
          y ← 2 ~> "Two"
        } yield x + y
      }
      assert(result.run.value ≟ \/-(3))
      assert(result.run.written ≟ node("Parent", true, node("One", true), node("Two", true)))
    }

    def `folding a result under a parent`() = {
      val result = "Parent" ~<+ (List(1 ~> "One", 2 ~> "Two"), (_: List[Int]).sum)
      assert(result.run.value ≟ \/-(3))
      assert(result.run.written ≟ node("Parent", true, node("One", true), node("Two", true)))
    }

    def `create a new parent above the existing branch and give it the description when the hoisted branch has a description`() = {
      val result = "Grandparent" ~< {
        "Parent" ~< {
          for {
            x ← 1 ~> "One"
            y ← 2 ~> "Two"
          } yield x + y
        }
      }
      assert(result.run.value ≟ \/-(3))
      assert(result.run.written ≟ node("Grandparent", true, node("Parent", true, node("One", true), node("Two", true))))
    }
  }

  private def node(description: String, success: Boolean, children: Tree[LogTreeLabel[Nothing]]*) =
    Tree.node(DescribedLogTreeLabel(description, success), children.toStream)

  private def node(success: Boolean, children: Tree[LogTreeLabel[Nothing]]*) =
    Tree.node(UndescribedLogTreeLabel(success), children.toStream)
}