package smithy4s.zio.schema

import smithy4s.schema.Schema
import smithy4s.schema.Schema.*
import smithy4s.{Blob, ShapeId, Timestamp}
import zio.Scope
import zio.schema.{Schema => ZSchema}
import zio.test.{Spec, TestEnvironment, assertTrue}

object ZSchemaVisitorSpec extends zio.test.ZIOSpecDefault {

  def visitor[A]: Schema[A] => ZSchema[A] =
    SchemaVisitorZSchemaGen.fromSchema(_)

  // Test data structures
  case class RecursiveFoo(foo: Option[RecursiveFoo])
  object RecursiveFoo {
    val schema: Schema[RecursiveFoo] =
      recursive {
        val foos = schema.optional[RecursiveFoo]("foo", _.foo)
        struct(foos)(RecursiveFoo.apply)
      }.withId(ShapeId("", "RecursiveFoo"))
  }

  sealed trait IntOrString
  object IntOrString {
    case class IntValue(value: Int) extends IntOrString
    case class StringValue(value: String) extends IntOrString

    val schema: Schema[IntOrString] = {
      val intAlt = int.oneOf[IntOrString]("intValue", IntValue(_)) {
        case IntValue(i) => i
      }
      val strAlt = string.oneOf[IntOrString]("stringValue", StringValue(_)) {
        case StringValue(s) => s
      }
      union(intAlt, strAlt).reflective.withId(ShapeId("", "IntOrString"))
    }
  }

  sealed trait IntOrInt
  object IntOrInt {
    case class IntValue0(value: Int) extends IntOrInt
    case class IntValue1(value: Int) extends IntOrInt

    val schema: Schema[IntOrInt] = {
      val int0 = int.oneOf[IntOrInt]("intValue0", IntValue0(_)) {
        case IntValue0(i) => i
      }
      val int1 = int.oneOf[IntOrInt]("intValue1", IntValue1(_)) {
        case IntValue1(i) => i
      }
      union(int0, int1).reflective.withId(ShapeId("", "IntOrInt"))
    }
  }

  // Helper to test schema roundtrip: encode then decode
  def roundtrip[A](schema: ZSchema[A], value: A): Boolean = {
    // Use ZIO Schema's DynamicValue for roundtrip testing
    val dynamic = zio.schema.DynamicValue.fromSchemaAndValue(schema, value)
    dynamic.toTypedValue(schema) match {
      case Left(_)        => false
      case Right(decoded) => decoded == value
    }
  }

  override def spec: Spec[TestEnvironment with Scope, Any] = {
    suite("ZSchemaVisitorSpec")(
      test("int") {
        val schema: Schema[Int] = int
        val zschema = visitor(schema)
        assertTrue(
          roundtrip(zschema, 42),
          roundtrip(zschema, 0),
          roundtrip(zschema, -1)
        )
      },
      test("string") {
        val schema: Schema[String] = string
        val zschema = visitor(schema)
        assertTrue(
          roundtrip(zschema, "hello"),
          roundtrip(zschema, ""),
          roundtrip(zschema, "world")
        )
      },
      test("boolean") {
        val schema: Schema[Boolean] = boolean
        val zschema = visitor(schema)
        assertTrue(
          roundtrip(zschema, true),
          roundtrip(zschema, false)
        )
      },
      test("long") {
        val schema: Schema[Long] = long
        val zschema = visitor(schema)
        assertTrue(
          roundtrip(zschema, 42L),
          roundtrip(zschema, 0L),
          roundtrip(zschema, Long.MaxValue)
        )
      },
      test("short") {
        val schema: Schema[Short] = short
        val zschema = visitor(schema)
        assertTrue(
          roundtrip(zschema, 42.toShort),
          roundtrip(zschema, 0.toShort)
        )
      },
      test("byte") {
        val schema: Schema[Byte] = byte
        val zschema = visitor(schema)
        assertTrue(
          roundtrip(zschema, 42.toByte),
          roundtrip(zschema, 0.toByte)
        )
      },
      test("double") {
        val schema: Schema[Double] = double
        val zschema = visitor(schema)
        assertTrue(
          roundtrip(zschema, 42.0),
          roundtrip(zschema, 0.0),
          roundtrip(zschema, Double.MaxValue)
        )
      },
      test("float") {
        val schema: Schema[Float] = float
        val zschema = visitor(schema)
        assertTrue(
          roundtrip(zschema, 42.0f),
          roundtrip(zschema, 0.0f)
        )
      },
      test("bigint") {
        val schema: Schema[BigInt] = bigint
        val zschema = visitor(schema)
        assertTrue(
          roundtrip(zschema, BigInt(42)),
          roundtrip(zschema, BigInt(0))
        )
      },
      test("bigdecimal") {
        val schema: Schema[BigDecimal] = bigdecimal
        val zschema = visitor(schema)
        assertTrue(
          roundtrip(zschema, BigDecimal(42.5)),
          roundtrip(zschema, BigDecimal(0))
        )
      },
      test("smithy4s Blob") {
        val schema: Schema[Blob] = blob
        val zschema = visitor(schema)
        assertTrue(
          roundtrip(zschema, Blob("hello")),
          roundtrip(zschema, Blob.empty)
        )
      },
      test("smithy4s timestamp") {
        val schema: Schema[Timestamp] = timestamp
        val now = java.time.Instant.now()
        val t1 = Timestamp.fromEpochSecond(now.getEpochSecond)
        val zschema = visitor(schema)
        assertTrue(
          roundtrip(zschema, t1)
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
        val zschema = visitor(Foo.schema)
        assertTrue(
          roundtrip(zschema, Foo(List(1, 2, 3))),
          roundtrip(zschema, Foo(List.empty))
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
        val zschema = visitor(Foo.schema)
        assertTrue(
          roundtrip(zschema, Foo(Vector(1, 2, 3))),
          roundtrip(zschema, Foo(Vector.empty))
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
        val zschema = visitor(Foo.schema)
        assertTrue(
          roundtrip(zschema, Foo(Set(1, 2, 3))),
          roundtrip(zschema, Foo(Set.empty))
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
        val zschema = visitor(Foo.schema)
        assertTrue(
          roundtrip(zschema, Foo(IndexedSeq(1, 2, 3)))
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
        val zschema = visitor(Foo.schema)
        assertTrue(
          roundtrip(zschema, Foo(Map("a" -> 1, "b" -> 2))),
          roundtrip(zschema, Foo(Map.empty))
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
        val zschema = visitor(Foo.schema)
        assertTrue(
          roundtrip(zschema, Foo(42, "hello")),
          roundtrip(zschema, Foo(0, ""))
        )
      },
      test("struct: with optional field") {
        case class Foo(x: Int, y: Option[String])
        object Foo {
          val schema: Schema[Foo] = {
            val x = int.required[Foo]("x", _.x)
            val y = string.optional[Foo]("y", _.y)
            struct(x, y)(Foo.apply)
          }
        }
        val zschema = visitor(Foo.schema)
        assertTrue(
          roundtrip(zschema, Foo(42, Some("hello"))),
          roundtrip(zschema, Foo(42, None))
        )
      },
      test("recursion") {
        val zschema = visitor(RecursiveFoo.schema)
        assertTrue(
          roundtrip(zschema, RecursiveFoo(Some(RecursiveFoo(None)))),
          roundtrip(zschema, RecursiveFoo(None))
        )
      },
      test("union with different subtypes") {
        val zschema = visitor(IntOrString.schema)
        assertTrue(
          roundtrip(zschema, IntOrString.IntValue(42)),
          roundtrip(zschema, IntOrString.StringValue("hello"))
        )
      },
      test("union with same subtypes") {
        val zschema = visitor(IntOrInt.schema)
        assertTrue(
          roundtrip(zschema, IntOrInt.IntValue0(42)),
          roundtrip(zschema, IntOrInt.IntValue1(99))
        )
      },
      test("option") {
        val schema: Schema[Option[Int]] = int.option
        val zschema = visitor(schema)
        assertTrue(
          roundtrip(zschema, Some(42)),
          roundtrip(zschema, None)
        )
      },
      test("bijection") {
        case class Wrapper(value: String)
        val schema: Schema[Wrapper] = string.biject(s => Wrapper(s))(_.value)
        val zschema = visitor(schema)
        assertTrue(
          roundtrip(zschema, Wrapper("hello"))
        )
      }
    )
  }
}
