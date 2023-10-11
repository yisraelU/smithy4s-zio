package smithy4s.zio.prelude

package testcases

import smithy4s.schema.Schema._
import smithy4s.schema.Schema
import smithy4s.ShapeId

sealed trait IntOrString

object IntOrString {
  case class IntValue(value: Int) extends IntOrString

  case class StringValue(value: String) extends IntOrString

  val schema: Schema[IntOrString] = {
    val intValue = int.oneOf[IntOrString]("intValue", IntValue(_)) {
      case IntValue(int) => int
    }
    val stringValue = string.oneOf[IntOrString]("stringValue", StringValue(_)) {
      case StringValue(str) => str
    }
    union(intValue, stringValue).reflective.withId(ShapeId("", "IntOrString"))
  }
}
