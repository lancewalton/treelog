import java.util.UUID

import cats.Show
import cats.implicits._
import treelog.LogTreeSyntax

case class PersonKey(uuid: UUID = UUID.randomUUID())
case class Person(key: PersonKey, name: String)

object AnnotationsExample extends App with LogTreeSyntax[PersonKey] {
  // We need this implicit so that we can cats.Show the result
  implicit val personKeyShow: Show[PersonKey] = Show.show[PersonKey](_.uuid.toString)

  val result: DescribedComputation[List[String]] = peopleToGreet() ~>* ("Greeting everybody", greet)

  // This will not compile unless we define a scalaz.Show for PersonKey (as above)
  println(result.value.written.show)

  // The above renders:
  // Greeting everybody
  //   Said hello to Lance - [5f08d72a-08c1-4bc5-8750-7639c1f0b2a5]
  //   Said hello to Channing - [eec2a576-1e87-4dbd-a97a-8e770c3ae85e]

  println(result.allAnnotations)
  // The above renders:
  // Set(PersonKey(d0ab89bb-0be1-426b-a1a5-b4b6bed1f63b), PersonKey(d9d2065b-bb95-452d-aa6f-f620bc67c229))

  // The '~~' operator annotates the node on the left with the object on the right
  private def greet(person: Person) = s"Hello, ${person.name}" ~> s"Said hello to ${person.name}" ~~ person.key

  private def peopleToGreet() = Person(PersonKey(), "Lance") :: Person(PersonKey(), "Channing") :: Nil
}
