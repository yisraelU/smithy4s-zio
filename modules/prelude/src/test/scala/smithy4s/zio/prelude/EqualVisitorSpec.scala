package smithy4s.zio.prelude

import smithy4s.schema.Schema
import smithy4s.schema.Schema.*
import smithy4s.zio.prelude.HashTestUtils.*
import smithy4s.zio.prelude.testcases.*
import smithy4s.zio.prelude.testcases.IntOrString.*
import smithy4s.{Blob, Hints, ShapeId, Timestamp}
import zio.Scope
import zio.prelude.Equal
import zio.test.{Spec, TestEnvironment, assertTrue}

object EqualVisitorSpec extends zio.test.ZIOSpecDefault {

  def visitor[A]: Schema[A] => Equal[A] = SchemaVisitorEqual.fromSchema(_)

  override def spec: Spec[TestEnvironment with Scope, Any] = {
    suite("EqualVisitorSpec")(
      test("int") {
        val schema: Schema[Int] = int
        val eq = visitor(schema)
        val intValue = 1
        assertTrue(eq.equal(intValue, 1), eq.notEqual(intValue, 2))
      },
      test("string") {
        val schema: Schema[String] = string
        val foo = "foo"
        val eq = visitor(schema)
        assertTrue(eq.equal(foo, "foo"), eq.notEqual(foo, "bar"))
      },
      test("boolean") {
        val schema: Schema[Boolean] = boolean
        val foo = true
        val eq = visitor(schema)
        assertTrue(eq.equal(foo, true), eq.notEqual(foo, false))
      },
      test("long") {
        val schema: Schema[Long] = long
        val foo = 1L
        val eq = visitor(schema)
        assertTrue(eq.equal(foo, 1L), eq.notEqual(foo, 2L))
      },
      test("short") {
        val schema: Schema[Short] = short
        val foo = 1.toShort
        val eq = visitor(schema)
        assertTrue(eq.equal(foo, 1.toShort), eq.notEqual(foo, 2.toShort))
      },
      test("byte") {
        val schema: Schema[Byte] = byte
        val foo = 1.toByte
        val eq = visitor(schema)
        assertTrue(eq.equal(foo, 1.toByte), eq.notEqual(foo, 2.toByte))
      },
      test("double") {
        val schema: Schema[Double] = double
        val foo = 1.0
        val eq = visitor(schema)
        assertTrue(eq.equal(foo, 1.0), eq.notEqual(foo, 2.0))
      },
      test("float") {
        val schema: Schema[Float] = float
        val foo = 1.0f
        val eq = visitor(schema)
        assertTrue(eq.equal(foo, 1.0f), eq.notEqual(foo, 2.0f))
      },
      test("bigint") {
        val schema: Schema[BigInt] = bigint
        val foo = BigInt(1)
        val eq = visitor(schema)
        assertTrue(eq.equal(foo, BigInt(1)), eq.notEqual(foo, BigInt(2)))
      },
      test("bigdecimal") {
        val schema: Schema[BigDecimal] = bigdecimal
        val foo = BigDecimal(1)
        val eq = visitor(schema)
        assertTrue(
          eq.equal(foo, BigDecimal(1)),
          eq.notEqual(foo, BigDecimal(2))
        )
      },
      test("smithy4s Blob") {
        val schema: Schema[Blob] = blob
        val fooBar = Blob("fooBar")
        val eq = visitor(schema)
        assertTrue(
          eq.equal(fooBar, Blob("fooBar")),
          eq.notEqual(fooBar, Blob("barFoo"))
        )
      },
      test("smithy4s timestamp Sanity Check") {
        val schema: Schema[Timestamp] = timestamp
        val now = java.time.Instant.now()
        val foo = getTimestamp(now)
        val foo1 = getTimestamp(now.plusSeconds(1))
        val eq = visitor(schema)
        assertTrue(eq.equal(foo, foo), eq.notEqual(foo, foo1))
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
        val eq = visitor(Foo.schema)
        assertTrue(
          eq.equal(foo, Foo(intList)),
          eq.notEqual(foo, Foo(List(1, 2)))
        )
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
        val eq = visitor(Foo.schema)
        assertTrue(eq.equal(foo, Foo(intSet)), eq.notEqual(foo, Foo(Set(1, 2))))

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
        val eq = visitor(Foo.schema)
        assertTrue(
          eq.equal(foo, Foo(intVector)),
          eq.notEqual(foo, Foo(Vector(1, 2)))
        )
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
        val eq = visitor(Foo.schema)
        assertTrue(
          eq.equal(foo, Foo(intIndexedSeq)),
          eq.notEqual(foo, Foo(IndexedSeq(1, 2)))
        )
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
        val eq = visitor(Foo.schema)
        assertTrue(
          eq.equal(foo, Foo(intMap)),
          eq.notEqual(foo, Foo(Map("foo" -> 1)))
        )
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
        val eq = visitor(Foo.schema)
        assertTrue(
          eq.equal(foo, Foo("foo", "bar")),
          eq.notEqual(foo, Foo("foo", "baz"))
        )
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
        val eq = visitor(Foo.schema)
        assertTrue(
          eq.equal(foo, Foo("foo", None)),
          eq.notEqual(foo, Foo("foo", Some("bar")))
        )
      },
      test("recursion") {
        val foo = RecursiveFoo(Some(RecursiveFoo(None)))
        val eq = visitor(RecursiveFoo.schema)
        assertTrue(
          eq.equal(foo, RecursiveFoo(Some(RecursiveFoo(None)))),
          eq.notEqual(
            foo,
            RecursiveFoo(Some(RecursiveFoo(Some(RecursiveFoo(None)))))
          )
        )
      },
      test("union with different subtypes") {
        val foo0 = IntValue(1)
        StringValue("foo")
        val eq = visitor(IntOrString.schema)
        assertTrue(
          eq.equal(foo0, IntValue(1)),
          eq.notEqual(foo0, StringValue("foo"))
        )
      },
      test("union with the same subtypes") {
        val foo0 = IntOrInt.IntValue0(1)
        val foo1 = IntOrInt.IntValue1(1)
        val eq = visitor(IntOrInt.schema)
        assertTrue(
          eq.equal(foo0, IntOrInt.IntValue0(1)),
          eq.notEqual(foo0, foo1)
        )
      },
      test("default enum") {
        val foo = FooBar.Foo
        val eq = visitor(FooBar.schema)
        assertTrue(eq.equal(foo, FooBar.Foo), eq.notEqual(foo, FooBar.Bar))
      },
      test("int enum") {
        val foo = IntFooBar.Foo
        IntFooBar.Bar
        val eq = visitor(IntFooBar.schema)
        assertTrue(
          eq.equal(foo, IntFooBar.Foo),
          eq.notEqual(foo, IntFooBar.Bar)
        )
      }
    )

  }

}
object EqualTestUtils {
  implicit val recursiveOptionEqual: Equal[RecursiveFoo] = {
    (l: RecursiveFoo, r: RecursiveFoo) =>
      Equal[Option[RecursiveFoo]].equal(l.foo, r.foo)
  }

  def getTimestamp: Timestamp = {
    val now = java.time.Instant.now()
    Timestamp.fromEpochSecond(now.getEpochSecond)
  }

}
