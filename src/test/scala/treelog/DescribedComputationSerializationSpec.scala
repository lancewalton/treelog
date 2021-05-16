package treelog

import cats.implicits._
import org.scalatest.matchers.must.Matchers
import org.scalatest.refspec.RefSpec

class DescribedComputationSerializationSpec extends RefSpec with Matchers {
  import LogTreeSyntaxWithoutAnnotations._

  override def convertToEqualizer[T](left: T): Equalizer[T] = super.convertToEqualizer(left)

  def `toSerializableForm and fromSerializableForm are inverses`() = {
    val dc =
      for {
        _      <- 1 ~> "Hello"
        result <- 2 ~> "Goodbye"
      } yield result

    val serialisedAndDeserialised = fromSerializableForm(toSerializableForm(dc))
    assert(serialisedAndDeserialised.value.value === dc.value.value)
    assert(serialisedAndDeserialised.value.written === dc.value.written)
  }

}
