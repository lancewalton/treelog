package treelog

import org.scalacheck.{Arbitrary, Prop, Properties}
import treelog.LogTreeSyntaxWithoutAnnotations._

import scala.collection.mutable.ListBuffer
import scala.language.higherKinds
import scalaz.Scalaz._
import scalaz._
import scalaz.scalacheck.ScalazProperties._

class DescribedComputationTSpec extends Properties("DescribedComputationTSpec") {

  def checkAll(props: Properties): Seq[ListBuffer[(String, Prop)]] = {
    for ((name, prop) <- props.properties) yield {
      property(name) = prop
    }
  }
  def newProperties(name: String)(f: Properties => Unit): Properties = {
    val p = new Properties(name)
    f(p)
    p
  }

  // checking laws
  import scalaz.scalacheck.ScalazArbitrary._

  // required arbitraries
  implicit def describedComputationTArbitrary[F[_]: Monad, A](implicit a: Arbitrary[F[A]]):Arbitrary[DescribedComputationT[F, A]]
  = Arbitrary( for( r <- a.arbitrary ) yield r ~> "test description" )


  // Do we need more combinations ??
  checkAll(newProperties("DescribedComputationT[Option,?]") { p =>
    p.include(functor.laws[DescribedComputationT[Option,?]])
    p.include(monad.laws[DescribedComputationT[Option,?]])
    p.include(monadTrans.laws[DescribedComputationT, Option])
  })

  checkAll(newProperties("DescribedComputationT[Maybe,?]") { p =>
    p.include(functor.laws[DescribedComputationT[Maybe,?]])
    p.include(monad.laws[DescribedComputationT[Maybe,?]])
    p.include(monadTrans.laws[DescribedComputationT, Maybe])
  })

  checkAll(newProperties("DescribedComputationT[List,?]") { p =>
    p.include(functor.laws[DescribedComputationT[List,?]])
    p.include(monad.laws[DescribedComputationT[List,?]])
    p.include(monadTrans.laws[DescribedComputationT, List])
  })

  checkAll(newProperties("DescribedComputationT[Tree,?]") { p =>
    p.include(functor.laws[DescribedComputationT[Tree,?]])
    p.include(monad.laws[DescribedComputationT[Tree,?]])
    p.include(monadTrans.laws[DescribedComputationT, Tree])
  })

}
