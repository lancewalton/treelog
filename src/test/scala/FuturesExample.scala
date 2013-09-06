import treelog._
import treelog.LogTreeSyntaxWithoutAnnotations._
import scalaz._
import Scalaz._
import scala.concurrent._
import scala.concurrent.duration._

object FuturesExample extends App {

  implicit val ec = ExecutionContext.global

  val future1: Future[DescribedComputation[Int]] = Future(1 ~> "Got 1")
  //val future2: Future[DescribedComputation[Int]] = Future(failure("Couldn't get a 2"))
  val future2: Future[DescribedComputation[Int]] = Future(2 ~> "Got 2")
  val future3: Future[DescribedComputation[Int]] = Future(3 ~> "Got 3")

  val lf: Future[List[DescribedComputation[Int]]] = Future.sequence(future1 :: future2 :: future3 :: Nil)

  val summedFuture: Future[DescribedComputation[Int]] = lf map doSum

  def doSum(l: List[DescribedComputation[Int]]): DescribedComputation[Int] =
    "Summed up" ~<+ (l, (_: List[Int]).sum)

  summedFuture.foreach(l ⇒ {
    val log = l.run.written
    val sum = l.run.value
    println(log.shows)
    println(sum.shows)
  })

  Thread.sleep(1000)
}