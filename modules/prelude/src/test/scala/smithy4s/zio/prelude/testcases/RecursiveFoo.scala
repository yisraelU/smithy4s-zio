package smithy4s.zio.prelude
package testcases

import smithy4s.schema.Schema
import smithy4s.schema.Schema.*
import smithy4s.ShapeId

case class RecursiveFoo(foo: Option[RecursiveFoo])

object RecursiveFoo {
  val schema: Schema[RecursiveFoo] =
    recursive {
      val foos = schema.optional[RecursiveFoo]("foo", _.foo)
      struct(foos)(RecursiveFoo.apply)
    }.withId(ShapeId("", "RecursiveFoo"))
}
