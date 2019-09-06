package treelog

import org.scalatest.refspec.RefSpec
import org.scalatest.MustMatchers
import cats.implicits._

class DescribedComputationSerializationSpec extends RefSpec with MustMatchers {
  import LogTreeSyntaxWithoutAnnotations._

  override def convertToEqualizer[T](left: T): Equalizer[T] = super.convertToEqualizer(left)

  def `toSerializableForm and fromSerializableForm are inverses`() = {
    val dc =
      for {
        _ <- 1 ~> "Hello"
        result <- 2 ~> "Goodbye"
      } yield result

    val serialisedAndDeserialised = fromSerializableForm(toSerializableForm(dc))
    assert(serialisedAndDeserialised.value.value === dc.value.value)
    assert(serialisedAndDeserialised.value.written === dc.value.written)
  }
}
