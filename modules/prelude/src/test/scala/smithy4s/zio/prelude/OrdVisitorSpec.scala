package smithy4s.zio.prelude

import smithy4s.schema.Schema
import smithy4s.schema.Schema.*
import smithy4s.zio.prelude.HashTestUtils.*
import smithy4s.zio.prelude.testcases.*
import smithy4s.zio.prelude.testcases.IntOrString.*
import smithy4s.{Blob, Hints, ShapeId, Timestamp}
import zio.Scope
import zio.prelude.Ord
import zio.test.{Spec, TestEnvironment, assertTrue}

object OrdVisitorSpec extends zio.test.ZIOSpecDefault {

  def visitor[A]: Schema[A] => Ord[A] = SchemaVisitorOrd.fromSchema(_)

  override def spec: Spec[TestEnvironment with Scope, Any] = {
    suite("OrdVisitorSpec")(
      test("int") {
        val schema: Schema[Int] = int
        val ord = visitor(schema)
        assertTrue(
          ord.compare(1, 2) == zio.prelude.Ordering.LessThan,
          ord.compare(2, 1) == zio.prelude.Ordering.GreaterThan,
          ord.compare(1, 1) == zio.prelude.Ordering.Equals
        )
      },
      test("string") {
        val schema: Schema[String] = string
        val ord = visitor(schema)
        assertTrue(
          ord.compare("a", "b") == zio.prelude.Ordering.LessThan,
          ord.compare("b", "a") == zio.prelude.Ordering.GreaterThan,
          ord.compare("a", "a") == zio.prelude.Ordering.Equals
        )
      },
      test("boolean") {
        val schema: Schema[Boolean] = boolean
        val ord = visitor(schema)
        assertTrue(
          ord.compare(false, true) == zio.prelude.Ordering.LessThan,
          ord.compare(true, false) == zio.prelude.Ordering.GreaterThan,
          ord.compare(true, true) == zio.prelude.Ordering.Equals
        )
      },
      test("long") {
        val schema: Schema[Long] = long
        val ord = visitor(schema)
        assertTrue(
          ord.compare(1L, 2L) == zio.prelude.Ordering.LessThan,
          ord.compare(2L, 1L) == zio.prelude.Ordering.GreaterThan,
          ord.compare(1L, 1L) == zio.prelude.Ordering.Equals
        )
      },
      test("short") {
        val schema: Schema[Short] = short
        val ord = visitor(schema)
        assertTrue(
          ord.compare(1.toShort, 2.toShort) == zio.prelude.Ordering.LessThan,
          ord.compare(2.toShort, 1.toShort) == zio.prelude.Ordering.GreaterThan,
          ord.compare(1.toShort, 1.toShort) == zio.prelude.Ordering.Equals
        )
      },
      test("byte") {
        val schema: Schema[Byte] = byte
        val ord = visitor(schema)
        assertTrue(
          ord.compare(1.toByte, 2.toByte) == zio.prelude.Ordering.LessThan,
          ord.compare(2.toByte, 1.toByte) == zio.prelude.Ordering.GreaterThan,
          ord.compare(1.toByte, 1.toByte) == zio.prelude.Ordering.Equals
        )
      },
      test("double") {
        val schema: Schema[Double] = double
        val ord = visitor(schema)
        assertTrue(
          ord.compare(1.0, 2.0) == zio.prelude.Ordering.LessThan,
          ord.compare(2.0, 1.0) == zio.prelude.Ordering.GreaterThan,
          ord.compare(1.0, 1.0) == zio.prelude.Ordering.Equals
        )
      },
      test("float") {
        val schema: Schema[Float] = float
        val ord = visitor(schema)
        assertTrue(
          ord.compare(1.0f, 2.0f) == zio.prelude.Ordering.LessThan,
          ord.compare(2.0f, 1.0f) == zio.prelude.Ordering.GreaterThan,
          ord.compare(1.0f, 1.0f) == zio.prelude.Ordering.Equals
        )
      },
      test("bigint") {
        val schema: Schema[BigInt] = bigint
        val ord = visitor(schema)
        assertTrue(
          ord.compare(BigInt(1), BigInt(2)) == zio.prelude.Ordering.LessThan,
          ord.compare(BigInt(2), BigInt(1)) == zio.prelude.Ordering.GreaterThan,
          ord.compare(BigInt(1), BigInt(1)) == zio.prelude.Ordering.Equals
        )
      },
      test("bigdecimal") {
        val schema: Schema[BigDecimal] = bigdecimal
        val ord = visitor(schema)
        assertTrue(
          ord.compare(
            BigDecimal(1),
            BigDecimal(2)
          ) == zio.prelude.Ordering.LessThan,
          ord.compare(
            BigDecimal(2),
            BigDecimal(1)
          ) == zio.prelude.Ordering.GreaterThan,
          ord.compare(
            BigDecimal(1),
            BigDecimal(1)
          ) == zio.prelude.Ordering.Equals
        )
      },
      test("smithy4s Blob") {
        val schema: Schema[Blob] = blob
        val ord = visitor(schema)
        assertTrue(
          ord
            .compare(Blob("aaa"), Blob("bbb")) == zio.prelude.Ordering.LessThan,
          ord.compare(
            Blob("bbb"),
            Blob("aaa")
          ) == zio.prelude.Ordering.GreaterThan,
          ord.compare(Blob("aaa"), Blob("aaa")) == zio.prelude.Ordering.Equals
        )
      },
      test("smithy4s timestamp") {
        val schema: Schema[Timestamp] = timestamp
        val now = java.time.Instant.now()
        val t1 = getTimestamp(now)
        val t2 = getTimestamp(now.plusSeconds(1))
        val ord = visitor(schema)
        assertTrue(
          ord.compare(t1, t2) == zio.prelude.Ordering.LessThan,
          ord.compare(t2, t1) == zio.prelude.Ordering.GreaterThan,
          ord.compare(t1, t1) == zio.prelude.Ordering.Equals
        )
      },
      test("list") {
        case class Foo(foos: List[Int])
        object Foo {
          val schema: Schema[Foo] = {
            val foos = list(int).required[Foo]("foos", _.foos)
            struct(foos)(Foo.apply)
          }
        }
        val ord = visitor(Foo.schema)
        val foo1 = Foo(List(1, 2, 3))
        val foo2 = Foo(List(1, 2, 4))
        val foo3 = Foo(List(1, 2))
        assertTrue(
          ord.compare(foo1, foo2) == zio.prelude.Ordering.LessThan,
          ord.compare(foo2, foo1) == zio.prelude.Ordering.GreaterThan,
          ord.compare(foo1, foo1) == zio.prelude.Ordering.Equals,
          ord.compare(
            foo3,
            foo1
          ) == zio.prelude.Ordering.LessThan // shorter list is less
        )
      },
      test("vector") {
        case class Foo(foos: Vector[Int])
        object Foo {
          val schema: Schema[Foo] = {
            val foos = vector(int).required[Foo]("foos", _.foos)
            struct(foos)(Foo.apply)
          }
        }
        val ord = visitor(Foo.schema)
        val foo1 = Foo(Vector(1, 2, 3))
        val foo2 = Foo(Vector(1, 2, 4))
        assertTrue(
          ord.compare(foo1, foo2) == zio.prelude.Ordering.LessThan,
          ord.compare(foo2, foo1) == zio.prelude.Ordering.GreaterThan,
          ord.compare(foo1, foo1) == zio.prelude.Ordering.Equals
        )
      },
      test("set") {
        case class Foo(foos: Set[Int])
        object Foo {
          val schema: Schema[Foo] = {
            val foos = set(int).required[Foo]("foos", _.foos)
            struct(foos)(Foo.apply)
          }
        }
        val ord = visitor(Foo.schema)
        val foo1 = Foo(Set(1, 2, 3))
        val foo2 = Foo(Set(1, 2, 4))
        assertTrue(
          ord.compare(foo1, foo2) == zio.prelude.Ordering.LessThan,
          ord.compare(foo2, foo1) == zio.prelude.Ordering.GreaterThan,
          ord.compare(foo1, foo1) == zio.prelude.Ordering.Equals
        )
      },
      test("indexedSeq") {
        case class Foo(foos: IndexedSeq[Int])
        object Foo {
          val schema: Schema[Foo] = {
            val foos = indexedSeq(int).required[Foo]("foos", _.foos)
            struct(foos)(Foo.apply)
          }
        }
        val ord = visitor(Foo.schema)
        val foo1 = Foo(IndexedSeq(1, 2, 3))
        val foo2 = Foo(IndexedSeq(1, 2, 4))
        assertTrue(
          ord.compare(foo1, foo2) == zio.prelude.Ordering.LessThan,
          ord.compare(foo2, foo1) == zio.prelude.Ordering.GreaterThan,
          ord.compare(foo1, foo1) == zio.prelude.Ordering.Equals
        )
      },
      test("map") {
        case class Foo(foos: Map[String, Int])
        object Foo {
          val schema: Schema[Foo] = {
            val foos = map(string, int).required[Foo]("foos", _.foos)
            struct(foos)(Foo.apply)
          }
        }
        val ord = visitor(Foo.schema)
        val foo1 = Foo(Map("a" -> 1, "b" -> 2))
        val foo2 = Foo(Map("a" -> 1, "b" -> 3))
        val foo3 = Foo(Map("a" -> 1, "c" -> 2))
        assertTrue(
          ord.compare(foo1, foo2) == zio.prelude.Ordering.LessThan,
          ord.compare(foo2, foo1) == zio.prelude.Ordering.GreaterThan,
          ord.compare(foo1, foo1) == zio.prelude.Ordering.Equals,
          ord.compare(foo1, foo3) == zio.prelude.Ordering.LessThan
        )
      },
      test("struct") {
        case class Foo(x: Int, y: String)
        object Foo {
          val schema: Schema[Foo] = {
            val x = int.required[Foo]("x", _.x)
            val y = string.required[Foo]("y", _.y)
            struct(x, y)(Foo.apply)
          }
        }
        val ord = visitor(Foo.schema)
        val foo1 = Foo(42, "foo")
        val foo2 = Foo(43, "foo")
        val foo3 = Foo(42, "bar")
        assertTrue(
          ord.compare(foo1, foo2) == zio.prelude.Ordering.LessThan,
          ord.compare(foo2, foo1) == zio.prelude.Ordering.GreaterThan,
          ord.compare(foo1, foo1) == zio.prelude.Ordering.Equals,
          ord.compare(
            foo3,
            foo1
          ) == zio.prelude.Ordering.LessThan // "bar" < "foo"
        )
      },
      test("struct: empty optional") {
        case class Foo(foos: Option[Int])
        object Foo {
          val schema: Schema[Foo] = {
            val foos = int.optional[Foo]("foos", _.foos)
            struct(foos)(Foo.apply)
          }
        }
        val ord = visitor(Foo.schema)
        val foo1 = Foo(Some(1))
        val foo2 = Foo(Some(2))
        val foo3 = Foo(None)
        assertTrue(
          ord.compare(foo1, foo2) == zio.prelude.Ordering.LessThan,
          ord.compare(foo2, foo1) == zio.prelude.Ordering.GreaterThan,
          ord.compare(foo1, foo1) == zio.prelude.Ordering.Equals,
          ord.compare(
            foo3,
            foo1
          ) == zio.prelude.Ordering.LessThan // None < Some
        )
      },
      test("recursion") {
        val ord = visitor(RecursiveFoo.schema)
        val foo1 = RecursiveFoo(Some(RecursiveFoo(None)))
        val foo2 = RecursiveFoo(Some(RecursiveFoo(Some(RecursiveFoo(None)))))
        val foo3 = RecursiveFoo(None)
        assertTrue(
          ord.compare(foo1, foo2) == zio.prelude.Ordering.LessThan,
          ord.compare(foo2, foo1) == zio.prelude.Ordering.GreaterThan,
          ord.compare(foo1, foo1) == zio.prelude.Ordering.Equals,
          ord.compare(foo3, foo1) == zio.prelude.Ordering.LessThan
        )
      },
      test("default enum") {
        val ord = visitor(IntOrString.schema)
        val intValue1 = IntValue(1)
        val intValue2 = IntValue(2)
        val stringValue = StringValue("foo")
        assertTrue(
          ord.compare(intValue1, intValue2) == zio.prelude.Ordering.LessThan,
          ord.compare(intValue2, intValue1) == zio.prelude.Ordering.GreaterThan,
          ord.compare(intValue1, intValue1) == zio.prelude.Ordering.Equals,
          // Different alternatives are ordered by their position
          ord.compare(intValue1, stringValue) != zio.prelude.Ordering.Equals
        )
      },
      test("int enum") {
        val ord = visitor(IntFooBar.schema)
        val foo1 = IntFooBar.Foo
        val foo2 = IntFooBar.Bar
        assertTrue(
          ord.compare(foo1, foo2) != zio.prelude.Ordering.Equals,
          ord.compare(foo1, foo1) == zio.prelude.Ordering.Equals
        )
      },
      test("union with the same subtypes") {
        val ord = visitor(IntOrInt.schema)
        val left1 = IntOrInt.IntValue0(1)
        val left2 = IntOrInt.IntValue0(2)
        val right1 = IntOrInt.IntValue1(1)
        assertTrue(
          ord.compare(left1, left2) == zio.prelude.Ordering.LessThan,
          ord.compare(left2, left1) == zio.prelude.Ordering.GreaterThan,
          ord.compare(left1, left1) == zio.prelude.Ordering.Equals,
          // Different alternatives
          ord.compare(left1, right1) != zio.prelude.Ordering.Equals
        )
      },
      test("union with different subtypes") {
        val ord = visitor(IntOrString.schema)
        val int1 = IntOrString.IntValue(1)
        val int2 = IntOrString.IntValue(2)
        val stringValue = IntOrString.StringValue("foo")
        assertTrue(
          ord.compare(int1, int2) == zio.prelude.Ordering.LessThan,
          ord.compare(int2, int1) == zio.prelude.Ordering.GreaterThan,
          ord.compare(int1, int1) == zio.prelude.Ordering.Equals,
          // Different alternatives
          ord.compare(int1, stringValue) != zio.prelude.Ordering.Equals
        )
      }
    )
  }
}
