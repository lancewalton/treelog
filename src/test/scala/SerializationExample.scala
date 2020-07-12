import argonaut.Argonaut._
import argonaut._
import treelog._

import scalaz.Scalaz._
import scalaz._

case class Thing(id: Int, name: String)

object Thing {
  implicit val codec = casecodec2(Thing.apply, Thing.unapply)("id", "name")
  implicit val show = new Show[Int] {
    override def shows(k: Int) = k.toString
  }
}

// This defines the Argonaut JSON encoders and decoders we need in order to serialize and deserialize the serializable form
// of the DescribedComputation
object Codecs {
  private implicit val logTreeLabelEncoder: EncodeJson[LogTreeLabel[Int]] = EncodeJson { l =>
    ("success" := l.success) ->:
      ("annotations" := l.annotations) ->:
      l.fold(
        d => ("description" := d.description) ->: jEmptyObject,
        _ => jEmptyObject)
  }

  private implicit val logTreeLabelDecoder: DecodeJson[LogTreeLabel[Int]] = DecodeJson { c =>
    if ((c --\ "description").succeeded)
      for {
        success <- (c --\ "success").as[Boolean]
        annotations <- (c --\ "annotations").as[Set[Int]]
        description <- (c --\ "description").as[String]
      } yield DescribedLogTreeLabel(description, success, annotations)
    else
      for {
        success <- (c --\ "success").as[Boolean]
        annotations <- (c --\ "annotations").as[Set[Int]]
      } yield UndescribedLogTreeLabel(success, annotations)
  }

  implicit val logTreeLabelCodec = CodecJson.derived[LogTreeLabel[Int]]

  private implicit val serializableTreeEncoder: EncodeJson[SerializableTree[Int]] = EncodeJson { t =>
    ("label" := t.label) ->:
      ("children" := t.children) ->:
      jEmptyObject
  }

  private implicit val serializableTreeDecoder: DecodeJson[SerializableTree[Int]] = DecodeJson { c =>
    for {
      label <- (c --\ "label").as[LogTreeLabel[Int]]
      children <- (c --\ "children").as[List[SerializableTree[Int]]]
    } yield SerializableTree(label, children)
  }

  implicit val serializableTreeCodec = CodecJson.derived[SerializableTree[Int]]
}

object SerializationExample extends App with LogTreeSyntax[Int] {

  val result = listOfThings() ~>* ("Here are some things", things)

  println("Before serialization:")
  showDescribedComputation(result)

  // The above will print:
  // The log is:
  // Here are some things
  //   Here I described Thing1 - [1]
  //   Here I described Thing2 - [2]
  //
  // The value is:
  // \/-(List(Hello Thing1, Hello Thing2))

  // Now let's serialize the DescribedComputation into JSON
  // Turn the DescribedComputation into a serializable form.
  val serializableDescribedComputation: SerializableDescribedComputation[List[String]] = toSerializableForm(result)

  import Codecs._
  import DecodeJsonScalaz._
  import EncodeJsonScalaz._

  val json = serializableDescribedComputation.asJson.spaces2

  println
  println("Serialized:")
  println(json)
  // The above renders:
  /*
    [
      {
        "Right" : [
          "Hello Thing1",
          "Hello Thing2"
        ]
      },
      {
        "children" : [
          {
            "children" : [
            ],
            "label" : {
              "description" : "Here I described Thing1",
              "annotations" : [
                1
              ],
              "success" : true
            }
          },
          {
            "children" : [
            ],
            "label" : {
              "description" : "Here I described Thing2",
              "annotations" : [
                2
              ],
              "success" : true
            }
          }
        ],
        "label" : {
          "description" : "Here are some things",
          "annotations" : [
          ],
          "success" : true
        }
      }
    ]
  */

  // Now let's deserialize
  val parsed: \/[String, Json] = Parse.parse(json).toDisjunction
  val decoded = parsed.flatMap(_.jdecode[SerializableDescribedComputation[List[String]]].toEither.toDisjunction)
  val deserialized = decoded.map(ds => fromSerializableForm(ds))

  // That's all we need to do to deserialize

  deserialized.foreach { d =>
    println
    println("After serializing and deserializing:")
    showDescribedComputation(d)

    // The above will print:
    // The log is:
    // Here are some things
    //   Here I described Thing1 - [1]
    //   Here I described Thing2 - [2]
    //
    // The value is:
    // \/-(List(Hello Thing1, Hello Thing2))

    // Now let's carry on doing something:
    val moreStuff = "FTW!" ~< (for {
      things1And2 <- d ~> "Some things that have been serialized and deserialized"
      things3And4 <- List(Thing(3, "Thing3"), Thing(4, "Thing4")) ~>* ("Things that have not been serialized and deserialized", things)
    } yield things1And2 ::: things3And4)

    println
    println("After adding some things:")
    showDescribedComputation(moreStuff)

    // The above will print:
    // The log is:
    // The log is:
    // FTW!
    //   Some things that have been serialized and deserialized
    //     Here are some things
    //       Here I described Thing1 - [1]
    //       Here I described Thing2 - [2]
    //   Things that have not been serialized and deserialized
    //     Here I described Thing3 - [3]
    //     Here I described Thing4 - [4]
    //
    // The value is:
    // \/-(List(Hello Thing1, Hello Thing2, Hello Thing3, Hello Thing4))
  }

  private def showDescribedComputation(dc: DescribedComputation[List[String]]): Unit = {
    val runResult = dc.run

    // This will not compile unless we define a scalaz.Show for Thing (as above)
    println("The log is:")
    println(runResult.written.shows)

    println
    println("The value is:")
    println(runResult.value)
  }

  // The '~~' operator annotates the node on the left with the object on the right
  private def things(thing: Thing) = s"Hello ${thing.name}" ~> s"Here I described ${thing.name}" ~~ thing.id

  private def listOfThings() = Thing(1, "Thing1") :: Thing(2, "Thing2") :: Nil
}
