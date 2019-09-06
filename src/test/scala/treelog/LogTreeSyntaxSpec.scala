package treelog

import org.scalatest.refspec.RefSpec
import org.scalatest.MustMatchers
import treelog.Tree.{Leaf, Node}
import cats.implicits._
import ScalaCompat._

class LogTreeSyntaxSpec extends RefSpec with MustMatchers {
  import LogTreeSyntaxWithoutAnnotations._

  override def convertToEqualizer[T](left: T): Equalizer[T] = super.convertToEqualizer(left)

  // we need this for tests. If you just use Left("Fail") you end up with a \/[Nothing, String]
  // which upsets compiler when it cannot find an appropriate scalaz.Equal typeclass
  val aFailure: Either[String, String] = Left("Fail")

  object `success must` {
    def `produce a value with the given value on the right`() = { success(1, "Yay").value.value mustBe Right(1) }
    def `produce a written with a success leaf node and the given description`() = { assert(success(1, "Yay").value.written === node("Yay", true)) }
    def `produce a written with a success leaf node and no description`() = { assert(success(1).value.written === node(true)) }
  }

  object `failure must` {
    def `produce a value with the given message on the left`() = { failure("Boo").value.value mustBe Left("Boo") }
    def `produce a written with a failure leaf node and the given desscription`() = { assert(failure("Boo").value.written === node("Boo", false)) }
  }

  object `~~ must` {
    def `add the annotation to the relevant node's annotation set`() = {
      val s = new LogTreeSyntax[Int] {}
      import s._

      val node = success(1, "Yay") ~~ 1 ~~ 2
      assert(node.value.value === Right(1))
      assert(node.value.written === Leaf(DescribedLogTreeLabel("Yay", true, Set(1, 2))))
    }
  }

  object `allAnnotations must` {
    def `compile the set of all annotations in the tree`() = {
      val s = new LogTreeSyntax[Int] {}
      import s._

      val computation = "Parent" ~< {
        for {
          i <- 1 ~> "Child One" ~~ 1
          j <- 2 ~> "Child Two" ~~ 2
        } yield 3
      } ~~ 3

      assert(computation.allAnnotations === Set(1, 2, 3))
    }
  }

  object `leaf creation with ~> and a string must` {
    def `do the same as 'success'`() = { assert((1 ~> "Foo").value.run === success(1, "Foo").value.run) }
  }

  object `leaf creation with ~> and a function must` {
    def `do the same as 'success' after applying the function to the value`() = { assert((1 ~> (x => s"Foo: $x")).value.run === success(1, "Foo: 1").value.run) }
  }

  object `leaf creation with ~>! and a string must` {
    def `do the same as 'failure'`() = { assert((1 ~>! "Foo").value.run === failure("Foo").value.run) }
  }

  object `leaf creation with ~>! and a function must` {
    def `do the same as 'failure' after applying the function to the value`() = { assert((1 ~>! (x => s"Foo: $x")).value.run === failure("Foo: 1").value.run) }
  }

  object `boolean ~>? with a description must` {
    def `do the same as 'success' when the boolean is true`() = { assert((true ~>? "Foo").value.run === success(true, "Foo").value.run) }
    def `do the same as 'failure' when the boolean is false`() = { assert((false ~>? "Foo").value.run === failure("Foo").value.run) }
  }

  object `boolean ~>? with a failureDescription and a successDescription must` {
    def `do the same as 'success' with the successDescription when the boolean is true`() = { assert((true ~>? ("Foo", "Bar")).value.run === success(true, "Bar").value.run) }
    def `do the same as 'failure' with the failureDescription when the boolean is false`() = { assert((false ~>? ("Foo", "Bar")).value.run === failure("Foo").value.run) }
  }

  object `Option ~>? with a description must` {
    def `do the same as 'success' when the Option is Some`() = { assert((Some(2) ~>? "Foo").value.run === success(2, "Foo").value.run) }
    def `do the same as 'failure' when the Option is None`() = { assert((none[Int] ~>? "Foo").value.run === failure("Foo").value.run) }
  }

  object `Option ~>? with a noneDescription and a someDescription must` {
    def `do the same as 'success' when the Option is Some`() = { assert((Some(2) ~>? ("Foo", "Bar")).value.run === success(2, "Bar").value.run) }
    def `do the same as 'failure' when the Option is None`() = { assert((none[Int] ~>? ("Foo", "Bar")).value.run === failure("Foo").value.run) }
  }

  object `Option ~>? with a noneDescription and a function for someDescription must` {
    def `do the same as 'success' when the Option is Some`() = { assert((Some(2) ~>? ("Foo", x => "Bar: " + x)).value.run === success(2, "Bar: 2").value.run) }
    def `do the same as 'failure' when the Option is None`() = { assert((none[Int] ~>? ("Foo", x => "Bar: " + x)).value.run === failure("Foo").value.run) }
  }

  object `\\/ ~>? with a description must` {
    def `do the same as 'success' with right`() = { assert((Right(2) ~>? "Foo").value.run === success(2, "Foo").value.run) }
    def `do the same as 'failure' with left`() = { assert((aFailure ~>? "Foo").value.run === failure("Foo - Fail").value.run) }
  }

  object `\\/ ~>? with a leftDescription function and a rightDescription must` {
    def `do the same as 'success' with right`() = { assert((Right(2) ~>? (x => "Foo: " + x, "Bar")).value.run === success(2, "Bar").value.run) }
    def `do the same as 'failure' with left`() = { assert((aFailure ~>? (x => "Foo: " + x, "Bar")).value.run === failure("Foo: Fail").value.run) }
  }

  object `\\/ ~>? with a leftDescription function and a rightDescription function must` {
    def `do the same as 'success' with right`() = { assert((Right(2) ~>? (x => "Foo: " + x, x => "Bar: " + x)).value.run === success(2, "Bar: 2").value.run) }
    def `do the same as 'failure' with left`() = { assert((aFailure ~>? (x => "Foo: " + x, x => "Bar: " + x)).value.run === failure("Foo: Fail").value.run) }
  }

  object `~>* must` {
    def `return a success when all children are successes`() = {
      val result = List(1, 2) ~>* ("Parent", x => success(3 * x, "Child: " + x))
      assert(result.value.written === node("Parent", true, node("Child: 1", true), node("Child: 2", true)))
      assert(result.value.value === Right(List(3, 6)))
    }

    def `return a failure when one or more children is a failure`() = {
      val result = List(1, 2) ~>* ("Parent", x => (x === 1) ~>? s"Child: $x")
      assert(result.value.written === node("Parent", false, node("Child: 1", true), node("Child: 2", false)))
      assert(result.value.value === Left("Parent"))
    }
  }

  object `~>/ must` {
    def `return success with the folded value  and a log tree describing the fold when all parts are successes`() = {
      val result = List(1, 2, 3) ~>/ ("Foo", 0 ~> "Initial Value", (acc: Int, x: Int) => (acc + x) ~> (t => s"x=$x, result=$t"))
      assert(result.value.written ===
        node("Foo", true,
          node("Initial Value", true),
          node("x=1, result=1", true),
          node("x=2, result=3", true),
          node("x=3, result=6", true)))
      result.value.value must equal(Right(6))
    }

    def `return failure and a log tree describing the fold as far as it got`() = {
      def thing(acc: Int, x: Int) = if (x === 3) failure[Int]("No") else (acc + x) ~> (t => s"x=$x, result=$t")
      val result = List(1, 2, 3) ~>/ ("Bar", 0 ~> "Initial Value", thing)
      assert(result.value.written ===
        node("Bar", false,
          node("Initial Value", true),
          node("x=1, result=1", true),
          node("x=2, result=3", true),
          node("No", false)))
      result.value.value must equal(Left("Bar"))
    }
  }

  object `Hoisting a leaf into a branch must` {
    def `when the leaf is a success, create a success root node with the description with a single child which is the leaf`() = {
      val result =
        "Parent" ~< {
          for (x <- 1 ~> "Child") yield x
        }
      assert(result.value.written === node("Parent", true, node("Child", true)))
      assert(result.value.value === Right(1))
    }

    def `when the leaf is a failure, create a failure root node with the description with a single child which is the leaf`() = {
      val result: DescribedComputation[String] =
        "Parent" ~< {
          for (x <- failure[String]("Child")) yield x
        }
      assert(result.value.written === node("Parent", false, node("Child", false)))
      assert(result.value.value === Left("Parent"))
    }
  }

  object `Combining two leaves using a for comprehension must` {
    def `create a branch with the yielded value, without a description and with success equal to true when both leaves have success = true`() = {
      val result = for {
        x <- 1 ~> "One"
        y <- 2 ~> "Two"
      } yield x + y
      assert(result.value.value === Right(3))
      assert(result.value.written === node(true, node("One", true), node("Two", true)))
    }

    def `create a branch with the description of the first failed leaf on the left, without a description and with success equal to false when at least one of the leaves has success = false`() = {
      val result = for {
        x <- 1 ~> "One"
        y <- 2 ~>! "Two"
      } yield x + y
      assert(result.value.value === Left("Two"))
      assert(result.value.written === node(false, node("One", true), node("Two", false)))
    }

    def `preserve annotations`() = {
      val s = new LogTreeSyntax[Int] {}
      import s._

      {
        val computation = "Parent" ~< {
          for {
            i <- 1 ~> "Child One" ~~ 1
            j <- 2 ~> "Child Two"
          } yield 3
        }


        assert(computation.allAnnotations === Set(1))
      }

      {
        val computation = "Parent" ~< {
          for {
            i <- 1 ~> "Child One"
            j <- 2 ~> "Child Two" ~~ 2
          } yield 3
        }

        assert(computation.allAnnotations === Set(2))
      }

      {
        val computation = "Parent" ~< {
          for {
            i <- 1 ~> "Child One" ~~ 1
            j <- 2 ~> "Child Two" ~~ 2
          } yield 3
        }

        assert(computation.allAnnotations === Set(1, 2))
      }
    }
  }

  object `Hoisting a branch must` {
    def `copy the existing branch and give it the description when the hoisted branch has no description`() = {
      val result = "Parent" ~< {
        for {
          x <- 1 ~> "One"
          y <- 2 ~> "Two"
        } yield x + y
      }
      assert(result.value.value === Right(3))
      assert(result.value.written === node("Parent", true, node("One", true), node("Two", true)))
    }

    def `folding a result under a parent`() = {
      val result = "Parent" ~<+ (List(1 ~> "One", 2 ~> "Two"), (_: List[Int]).sum)
      assert(result.value.value === Right(3))
      assert(result.value.written === node("Parent", true, node("One", true), node("Two", true)))
    }

    def `create a new parent above the existing branch and give it the description when the hoisted branch has a description`() = {
      val result = "Grandparent" ~< {
        "Parent" ~< {
          for {
            x <- 1 ~> "One"
            y <- 2 ~> "Two"
          } yield x + y
        }
      }
      assert(result.value.value === Right(3))
      assert(result.value.written === node("Grandparent", true, node("Parent", true, node("One", true), node("Two", true))))
    }

    def `preserve annotations`() = {
      val s = new LogTreeSyntax[Int] {}
      import s._

      {
        val computationOne = "ParentOne" ~< ({
          for {
            i <- 1 ~> "Child One" ~~ 1
            j <- 2 ~> "Child Two" ~~ 2
          } yield 3
        } ~~ 3)

        val computationTwo = "ParentTwo" ~< ({
          for {
            i <- 1 ~> "Child Three" ~~ 4
            j <- 2 ~> "Child Four" ~~ 5
          } yield 3
        } ~~ 6)

        val computation = for {
          i <- computationOne
          j <- computationTwo
        } yield 0

        val hoistedComputation = computation ~> "Hoisted"

        assert(computation.allAnnotations === Set(1, 2, 3, 4, 5, 6))
        assert(hoistedComputation.allAnnotations === Set(1, 2, 3, 4, 5, 6))
      }

      {
        val computationOne = "ParentOne" ~< ({
          for {
            i <- 1 ~> "Child One" ~~ 1
            j <- 2 ~> "Child Two" ~~ 2
          } yield 3
        } ~~ 3)

        val computationTwo = {
          for {
            i <- 1 ~> "Child Three" ~~ 4
            j <- 2 ~> "Child Four" ~~ 5
          } yield 3
        } ~~ 6

        val computation = for {
          i <- computationOne
          j <- computationTwo
        } yield 0

        val hoistedComputation = computation ~> "Hoisted"

        assert(computation.allAnnotations === Set(1, 2, 3, 4, 5, 6))
        assert(hoistedComputation.allAnnotations === Set(1, 2, 3, 4, 5, 6))
      }

      {
        val computationOne = {
          for {
            i <- 1 ~> "Child One" ~~ 1
            j <- 2 ~> "Child Two" ~~ 2
          } yield 3
        } ~~ 3

        val computationTwo = "ParentTwo" ~< ({
          for {
            i <- 1 ~> "Child Three" ~~ 4
            j <- 2 ~> "Child Four" ~~ 5
          } yield 3
        } ~~ 6)

        val computation = for {
          i <- computationOne
          j <- computationTwo
        } yield 0

        val hoistedComputation = computation ~> "Hoisted"

        assert(computation.allAnnotations === Set(1, 2, 3, 4, 5, 6))
        assert(hoistedComputation.allAnnotations === Set(1, 2, 3, 4, 5, 6))
      }

      {
        val computationOne = {
          for {
            i <- 1 ~> "Child One" ~~ 1
            j <- 2 ~> "Child Two" ~~ 2
          } yield 3
        } ~~ 3

        val computationTwo = {
          for {
            i <- 1 ~> "Child Three" ~~ 4
            j <- 2 ~> "Child Four" ~~ 5
          } yield 3
        } ~~ 6

        val computation = for {
          i <- computationOne
          j <- computationTwo
        } yield 0

        val hoistedComputation = computation ~> "Hoisted"

        assert(computation.allAnnotations === Set(1, 2, 3, 4, 5, 6))
        assert(hoistedComputation.allAnnotations === Set(1, 2, 3, 4, 5, 6))
      }
    }
  }

  private def node(description: String, success: Boolean, children: Tree[LogTreeLabel[Nothing]]*) =
    Node(DescribedLogTreeLabel(description, success), children.toLazyList)

  private def node(success: Boolean, children: Tree[LogTreeLabel[Nothing]]*) =
    Node(UndescribedLogTreeLabel(success), children.toLazyList)
}
