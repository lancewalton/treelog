import treelog.LogTreeSyntaxWithoutAnnotations._

import scalaz._
import Scalaz._
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object DescribedComputationTExample extends App {

  // with Option
  val optionExample = for {
    one <- Option(1) ~> "1"
    two <- Option(2) ~> "2"
    res <- Option(one + two) ~> ("1 + 2: " + _)
  } yield res
  println(optionExample.run.get.run.written.show)


  // with List
  val listExample = for {
    v1 <- List(1,2,3,4) ~> ("v1: " + _)
    v2 <- List(10,20) ~> ("v2: " + _)
    v3 <- List(v1 + v2) ~> ("v1+v2: " + _)
  } yield v3
  println(listExample.run.map(_.run.written.show).mkString(",\n"))


  // with Tree
  val tree1 = 1.node(2.leaf, 3.leaf)
  val tree2 = 10.leaf
  val treeExample = for {
    v1 <- tree1 ~> ("value on tree1: " + _)
    v2 <- tree2 ~> ("value on tree2: " + _)
    v3 <- (v1 + v2).leaf ~> ("sum: " + _)
  } yield v3
  println(treeExample.run.map(_.run.written.show).drawTree)


  // with Future
  import scala.concurrent.ExecutionContext.Implicits.global
  val f1 = Future(1)
  val f2 = Future(2)
  val futureExample = for {
    v1 <- f1 ~> ("f1: " + _)
    v2 <- f2 ~> ("f2: " + _)
    v3 <- Future(v1 + v2) ~> ("f1 + f2: " + _)
  } yield v3
  println(Await.result(futureExample.run, Duration.Inf).run.written.show)


  // with IO
  import scalaz.effect.IO
  import IO._
  val dcT = for {
    _    <- putStr("input some string> ") ~> "output prompt"
    line <- readLn ~> ("input some string>: " + _)
    res <- putStrLn(line) ~> ("putStrLn(line): " + _)
  } yield {
    res
  }

  // input some string from stdio
  println("# Performing unsafePerformIO")
  val dc =  dcT.run.unsafePerformIO

  println("# Output described computation results")
  println(dc.run.written.shows)

}
