package smithy4s.zio.prelude
package testcases

import smithy4s.schema.Schema
import smithy4s.{Hints, ShapeId}

sealed abstract class IntFooBar(val stringValue: String, val intValue: Int)
    extends smithy4s.Enumeration.Value {
  override type EnumType = IntFooBar

  override def enumeration: smithy4s.Enumeration[EnumType] = IntFooBar

  val name = stringValue
  val value = stringValue
  val hints = Hints()

}

object IntFooBar
    extends smithy4s.Enumeration[IntFooBar]
    with smithy4s.ShapeTag.Companion[IntFooBar] {
  case object Foo extends IntFooBar("foo", 0)

  case object Bar extends IntFooBar("neq", 1)

  override def id: ShapeId = ShapeId("smithy4s.example", "FooBar")

  override def values: List[IntFooBar] = List(Foo, Bar)

  implicit val schema: Schema[IntFooBar] =
    Schema.intEnumeration[IntFooBar](List(Foo, Bar))

  override def hints: Hints = Hints.empty
}
