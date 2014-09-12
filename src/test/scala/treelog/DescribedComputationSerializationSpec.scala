package treelog

import org.scalatest._
import scalaz._
import Scalaz._

class DescribedComputationSerializationSpec extends Spec with MustMatchers {
  import LogTreeSyntaxWithoutAnnotations._

  def `toSerializableForm and fromSerializableForm are inverses`() = {
    val dc =
      for {
        _ ← 1 ~> "Hello"
        result ← 2 ~> "Goodbye"
      } yield result

    val serialisedAndDeserialised = fromSerializableForm(toSerializableForm(dc))
    assert(serialisedAndDeserialised.run.value ≟ dc.run.value)
    assert(serialisedAndDeserialised.run.written ≟ dc.run.written)
  }
}