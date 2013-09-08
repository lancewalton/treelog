import treelog._
import treelog.LogTreeSyntaxWithoutAnnotations._
import scalaz._
import Scalaz._
import scala.concurrent._
import scala.concurrent.duration._

object FuturesExample extends App {

  implicit val ec = ExecutionContext.global

  /*
   * Lets start with some extremely complicated parallel computations
   */
  val future1: Future[DescribedComputation[Int]] = Future(1 ~> "Got 1")
  val future2: Future[DescribedComputation[Int]] = Future(2 ~> "Got 2")
  // Use this to see how a failure is dealt with
  //val future2: Future[DescribedComputation[Int]] = Future(failure("Couldn't get a 2"))
  val future3: Future[DescribedComputation[Int]] = Future(3 ~> "Got 3")

  // Sequence the Futures to work on the results below
  val lf: Future[List[DescribedComputation[Int]]] = Future.sequence(future1 :: future2 :: future3 :: Nil)

  // map over the future, summing the result
  val summedFuture: Future[DescribedComputation[Int]] = lf map doSum

  /*
   * What we want here is a new root containing the logs of each parallel computation, and the result
   * of some operation on the values containined, in this case we are just going to sum them.
   * Have a look at the Scaladoc for treelog.LogTreeSyntax.BranchLabelingSyntax.~<+
   */
  def doSum(computations: List[DescribedComputation[Int]]): DescribedComputation[Int] =
    "Summed up" ~<+ (computations, (bits: List[Int]) ⇒ bits.sum)

  val ans = Await.result(summedFuture, 1.second)
  val log = ans.run.written
  val sum = ans.run.value
  println(log.shows)
  println(sum.shows)

  /*
   * Output is
   * Summed up
   *   Got 1
   *   Got 2
   *   Got 3
   * \/-(6)
   *
   * For the failure case the output is
   * Failed: Summed up
   *   Got 1
   *   Failed: Couldn't get a 2
   *   Got 3
   * -\/("Summed up")
   */
}