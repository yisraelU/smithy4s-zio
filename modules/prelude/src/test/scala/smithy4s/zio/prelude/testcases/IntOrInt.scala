package smithy4s.zio.prelude

package testcases

import smithy4s.schema.Schema.*
import smithy4s.schema.Schema
import smithy4s.ShapeId

sealed trait IntOrInt
object IntOrInt {
  case class IntValue0(value: Int) extends IntOrInt
  case class IntValue1(value: Int) extends IntOrInt

  val schema: Schema[IntOrInt] = {
    val intValue0 = int.oneOf[IntOrInt]("intValue0", IntValue0(_)) {
      case IntValue0(i) => i
    }
    val intValue1 = int.oneOf[IntOrInt]("intValue1", IntValue1(_)) {
      case IntValue1(i) => i
    }
    union(intValue0, intValue1).reflective
  }.withId(ShapeId("", "IntOrInt"))

}
