package smithy4s.zio.prelude

import scala.language.implicitConversions
import smithy4s.schema.Schema
import smithy4s.schema.Schema.*
import smithy4s.zio.prelude.HashTestUtils.*
import smithy4s.zio.prelude.instances.all.*
import smithy4s.zio.prelude.testcases.*
import smithy4s.zio.prelude.testcases.IntOrString.*
import smithy4s.{Blob, Hints, ShapeId, Timestamp}
import zio.Scope
import zio.prelude.Debug
import zio.prelude.Debug.{Renderer, Repr}
import zio.test.{Spec, TestEnvironment, assertTrue}

object DebugVisitorSpec extends zio.test.ZIOSpecDefault {

  def visitor[A]: Schema[A] => Debug[A] = SchemaVisitorDebug.fromSchema(_)

  implicit def render(repr: Repr): String = repr.render

  override def spec: Spec[TestEnvironment & Scope, Any] = {
    suite("DebugVisitorSpec")(
      test("int") {
        val schema: Schema[Int] = int
        val intValue = 1
        val output: String = visitor(schema).debug(intValue).render
        assertTrue(intValue.toString == output)
      },
      test("string") {
        val schema: Schema[String] = string
        val foo = "foo"
        val fooExpected = Repr.String(foo).render
        val output: String = visitor(schema).debug(foo)
        assertTrue(fooExpected == output)
      },
      test("boolean") {
        val schema: Schema[Boolean] = boolean
        val foo = true
        val fooExpected = Repr.Boolean(foo).render
        val output: String = visitor(schema).debug(foo).render
        assertTrue(fooExpected == output)
      },
      test("long") {
        val schema: Schema[Long] = long
        val foo = 1L
        val fooExpected = Repr.Long(foo).render
        val output: String = visitor(schema).debug(foo)
        assertTrue(fooExpected == output)
      },
      test("short") {
        val schema: Schema[Short] = short
        val foo = 1.toShort
        val fooExpected = Repr.Short(foo).render
        val output: String = visitor(schema).debug(foo)
        assertTrue(fooExpected == output)
      },
      test("byte") {
        val schema: Schema[Byte] = byte
        val foo = 1.toByte
        val output: String = visitor(schema).debug(foo)
        assertTrue(foo.toString() == output)

      },
      test("double") {
        val schema: Schema[Double] = double
        val foo = 1.0
        val output: String = visitor(schema).debug(foo)
        val expected = foo.toString()
        assertTrue(expected == output)

      },
      test("float") {
        val schema: Schema[Float] = float
        val foo = 1.0f
        val output: String = visitor(schema).debug(foo)
        val expected = Repr.Float(foo).render
        assertTrue(expected == output)
      },
      test("bigint") {
        val schema: Schema[BigInt] = bigint
        val foo = BigInt(1)
        val output: String = visitor(schema).debug(foo)
        val expected = Debug[BigInt].debug(foo).render
        assertTrue(expected == output)
      },
      test("bigdecimal") {
        val schema: Schema[BigDecimal] = bigdecimal
        val foo = BigDecimal(1)
        val output: String = visitor(schema).debug(foo)
        val expected = Debug[BigDecimal].debug(foo).render
        assertTrue(expected == output)
      },
      test("smithy4s Blob") {
        val schema: Schema[Blob] = blob
        val fooBar = Blob("fooBar")
        val output: String = visitor(schema).debug(fooBar)
        val expected = Debug[Blob].debug(fooBar).render
        assertTrue(expected == output)
      },
      test("smithy4s timestamp") {
        val schema: Schema[Timestamp] = timestamp
        val now = java.time.Instant.now()
        val foo = getTimestamp(now)
        val output: String = visitor(schema).debug(foo)
        val expected = Debug[Timestamp].debug(foo).render
        assertTrue(expected == output)
      },
      test("list") {
        case class Foo(foos: List[Int])
        object Foo {
          val schema: Schema[Foo] = {
            val foos = list(int)
              .required[Foo]("foos", _.foos)
            struct(foos)(Foo.apply)
          }.withId(ShapeId("", "Foo"))
        }
        val intList = List(1, 2, 3)
        val foo = Foo(intList)
        val expected = "Foo(foos = List(1, 2, 3))"
        val output: String = visitor(Foo.schema).debug(foo)
        assertTrue(expected == output)
      },
      test("set") {
        case class Foo(foos: Set[Int])
        object Foo {
          val schema: Schema[Foo] = {
            val foos = set(int)
              .required[Foo]("foos", _.foos)
            struct(foos)(Foo.apply)
          }.withId(ShapeId("", "Foo"))
        }
        val intSet = Set(1, 2, 3)
        val foo = Foo(intSet)
        val expected = "Foo(foos = Set(1, 2, 3))"
        val output: String = visitor(Foo.schema).debug(foo)
        assertTrue(expected == output)
      },
      test("vector") {
        case class Foo(foos: Vector[Int])
        object Foo {
          val schema: Schema[Foo] = {
            val foos = vector(int)
              .required[Foo]("foos", _.foos)
            struct(foos)(Foo.apply)
          }.withId(ShapeId("", "Foo"))
        }
        val intVector = Vector(1, 2, 3)
        val foo = Foo(intVector)
        val expected = "Foo(foos = Vector(1, 2, 3))"
        val output: String = visitor(Foo.schema).debug(foo)
        assertTrue(expected == output)
      },
      test("indexedSeq") {
        case class Foo(foos: IndexedSeq[Int])
        object Foo {
          val schema: Schema[Foo] = {
            val foos = indexedSeq(int)
              .required[Foo]("foos", _.foos)
            struct(foos)(Foo.apply)
          }.withId(ShapeId("", "Foo"))
        }
        val intIndexedSeq = IndexedSeq(1, 2, 3)
        val foo = Foo(intIndexedSeq)
        val expected = "Foo(foos = IndexedSeq(1, 2, 3))"
        val output: String = visitor(Foo.schema).debug(foo)
        assertTrue(expected == output)
      },
      test("map") {
        case class Foo(foos: Map[String, Int])
        object Foo {
          val schema: Schema[Foo] = {
            val foos = map(string, int)
              .required[Foo]("foos", _.foos)
            struct(foos)(Foo.apply)
          }.withId(ShapeId("", "Foo"))
        }
        val intMap = Map("foo" -> 1, "bar" -> 2)
        val foo = Foo(intMap)
        val expected = "Foo(foos = Map(\"foo\" -> 1, \"bar\" -> 2))"
        val output: String = visitor(Foo.schema).debug(foo)
        assertTrue(expected == output)
      },
      test("struct") {
        case class Foo(x: String, y: String)
        object Foo {
          val schema: Schema[Foo] = {
            StructSchema(
              ShapeId("", "Foo"),
              Hints.empty,
              Vector(
                string.required[Foo]("x", _.x),
                string.required[Foo]("y", _.y)
              ),
              arr =>
                Foo.apply(
                  arr(0).asInstanceOf[String],
                  arr(1).asInstanceOf[String]
                )
            )

          }
        }
        val foo = Foo("foo", "bar")
        val expected = "Foo(x = \"foo\", y = \"bar\")"
        val output: String = visitor(Foo.schema).debug(foo)
        assertTrue(expected == output)
      },
      test("struct: empty optional") {
        case class Foo(x: String, y: Option[String])
        object Foo {
          val schema: Schema[Foo] = {
            StructSchema(
              ShapeId("", "Foo"),
              Hints.empty,
              Vector(
                string.required[Foo]("x", _.x),
                string.optional[Foo]("y", _.y)
              ),
              arr =>
                Foo.apply(
                  arr(0).asInstanceOf[String],
                  arr(1).asInstanceOf[Option[String]]
                )
            )

          }
        }

        val foo = Foo("foo", None)
        val expected = "Foo(x = \"foo\", y = None)"
        val output: String = visitor(Foo.schema).debug(foo)
        assertTrue(expected == output)
      },
      test("recursion") {
        case class Foo(foo: Option[Foo])
        object Foo {
          val schema: Schema[Foo] =
            recursive {
              val foos = schema.optional[Foo]("foo", _.foo)
              struct(foos)(Foo.apply)
            }.withId(ShapeId("", "Foo"))
        }

        val foo = Foo(Some(Foo(None)))
        val output: String = visitor(Foo.schema).debug(foo)
        assertTrue(output == "Foo(foo = Some(Foo(foo = None)))")

      },
      test("union") {
        val foo0 = IntValue(1)
        val foo1 = StringValue("foo")
        val showOutput0: String =
          visitor(schema).debug(foo0).render(Renderer.Simple)
        val showOutput1: String =
          visitor(schema).debug(foo1).render(Renderer.Simple)
        assertTrue(showOutput0 == "IntOrString(intValue = 1)")
        assertTrue(showOutput1 == "IntOrString(stringValue = \"foo\")")

      },
      test("enumeration") {

        val foo = FooBar.Foo
        val showOutput: String = visitor(FooBar.schema).debug(foo)
        val fooExpected = Repr.String(foo.stringValue).render
        val bar = FooBar.Bar
        val showOutput1: String = visitor(FooBar.schema).debug(bar)
        val barExpected = Repr.String(bar.stringValue).render
        assertTrue(showOutput == fooExpected)
        assertTrue(showOutput1 == barExpected)
      }
    )
  }
}
