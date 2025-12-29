package smithy4s.zio.schema

import smithy4s.schema._
import smithy4s.{Schema, _}
import _root_.zio.schema.{Schema => ZSchema}
import _root_.zio.Chunk
import scala.collection.immutable.ListMap

object SchemaVisitorZSchemaGen extends CachedSchemaCompiler.Impl[ZSchema] {

  protected type Aux[A] = ZSchema[A]

  def fromSchema[A](
      schema: Schema[A],
      cache: Cache
  ): ZSchema[A] = {
    schema.compile(new SchemaVisitorZSchemaGen(cache))
  }

}

final class SchemaVisitorZSchemaGen(
    val cache: CompilationCache[ZSchema]
) extends SchemaVisitor.Cached[ZSchema] {
  self =>

  override def primitive[P](
      shapeId: ShapeId,
      hints: Hints,
      tag: Primitive[P]
  ): ZSchema[P] = {
    ZSchemaInstances.primSchemaPf(tag)
  }

  override def collection[C[_], A](
      shapeId: ShapeId,
      hints: Hints,
      tag: CollectionTag[C],
      member: Schema[A]
  ): ZSchema[C[A]] = {
    // Compile member schema ONCE, not on every collection access
    implicit val memberSchema: ZSchema[A] = member.compile(self)
    tag match {
      case CollectionTag.ListTag       => ZSchema.list[A]
      case CollectionTag.SetTag        => ZSchema.set[A]
      case CollectionTag.VectorTag     => ZSchema.vector[A]
      case CollectionTag.IndexedSeqTag =>
        ZSchema.vector[A].transform(_.toIndexedSeq, _.toVector)
    }
  }

  override def map[K, V](
      shapeId: ShapeId,
      hints: Hints,
      key: Schema[K],
      value: Schema[V]
  ): ZSchema[Map[K, V]] = {
    // Compile key and value schemas ONCE, not on every map access
    implicit val keySchema: ZSchema[K] = key.compile(self)
    implicit val valueSchema: ZSchema[V] = value.compile(self)
    ZSchema.map[K, V]
  }

  override def enumeration[E](
      shapeId: ShapeId,
      hints: Hints,
      tag: EnumTag[E],
      values: List[EnumValue[E]],
      total: E => EnumValue[E]
  ): ZSchema[E] = {
    // For enumerations, we map to String or Int schema and transform
    tag match {
      case EnumTag.IntEnum() =>
        ZSchema[Int].transformOrFail(
          (i: Int) =>
            values
              .find(_.intValue == i)
              .map(_.value)
              .toRight(s"Unknown enum value: $i"),
          (e: E) => Right(total(e).intValue)
        )
      case _ =>
        ZSchema[String].transformOrFail(
          (s: String) =>
            values
              .find(_.stringValue == s)
              .map(_.value)
              .toRight(s"Unknown enum value: $s"),
          (e: E) => Right(total(e).stringValue)
        )
    }
  }

  override def struct[S](
      shapeId: ShapeId,
      hints: Hints,
      fields: Vector[Field[S, ?]],
      make: IndexedSeq[Any] => S
  ): ZSchema[S] = {
    // We use GenericRecord (runtime-dynamic) and transform to the target type S
    // This allows smithy4s-generated types to access ZIO Schema's ecosystem
    // (codecs, validation, migrations, etc.)

    val typeId = _root_.zio.schema.TypeId.parse(shapeId.toString)

    // IMPORTANT: Compile all field schemas ONCE upfront, not on every field access
    val zFields: Seq[_root_.zio.schema.Schema.Field[ListMap[String, ?], Any]] =
      fields.map { field =>
        val fieldSchema: ZSchema[Any] =
          field.schema.compile(self).asInstanceOf[ZSchema[Any]]
        _root_.zio.schema.Schema.Field(
          name0 = field.label,
          schema0 = fieldSchema,
          annotations0 = Chunk.empty,
          validation0 = _root_.zio.schema.validation.Validation.succeed,
          get0 = (m: ListMap[String, ?]) => m(field.label),
          set0 = (m: ListMap[String, ?], a: Any) => m.updated(field.label, a)
        )
      }

    // Create the GenericRecord schema
    val recordSchema: ZSchema[ListMap[String, ?]] =
      _root_.zio.schema.Schema.record(typeId, zFields: _*)

    // Transform from ListMap[String, ?] to S
    recordSchema.transform(
      // Decode: ListMap[String, ?] => S
      // This lambda IS on the hot path, but only accesses pre-computed data
      (m: ListMap[String, ?]) => {
        val fieldValues = fields.map(f => m(f.label))
        make(fieldValues)
      },
      // Encode: S => ListMap[String, ?]
      // This lambda IS on the hot path, but only accesses pre-computed data
      (s: S) => {
        val entries = fields.map(f => f.label -> f.get(s))
        ListMap(entries: _*)
      }
    )
  }

  override def union[U](
      shapeId: ShapeId,
      hints: Hints,
      alternatives: Vector[Alt[U, ?]],
      dispatch: Alt.Dispatcher[U]
  ): ZSchema[U] = {
    // For unions, we use a tagged representation: (tag: String, value: DynamicValue)
    // DynamicValue is ZIO Schema's runtime-typed value representation
    // This works with all ZIO Schema codecs and provides runtime flexibility

    import _root_.zio.schema.DynamicValue

    // IMPORTANT: Compile all alternative schemas ONCE upfront
    val altSchemas: Map[String, ZSchema[Any]] =
      alternatives
        .map(alt =>
          alt.label -> alt.schema.compile(self).asInstanceOf[ZSchema[Any]]
        )
        .toMap

    // OPTIMIZATION: Create a dispatcher-based encoder to avoid iterating all alternatives
    // The encode function IS on the hot path
    val encoder: smithy4s.capability.EncoderK[Lambda[
      A => ZSchema[A]
    ], U => (String, DynamicValue)] =
      new smithy4s.capability.EncoderK[Lambda[
        A => ZSchema[A]
      ], U => (String, DynamicValue)] {
        def apply[A](schema: ZSchema[A], a: A): U => (String, DynamicValue) = {
          _ =>
            val dynValue = DynamicValue.fromSchemaAndValue(schema, a)
            // Need to find the label - this requires looking up the alt
            val label = alternatives
              .find(alt => alt.schema == schema)
              .map(_.label)
              .getOrElse("")
            (label, dynValue)
        }

        def absorb[A](f: A => U => (String, DynamicValue)): ZSchema[A] = {
          // This is called during precompilation, not on hot path
          altSchemas.head._2.asInstanceOf[ZSchema[A]]
        }
      }

    // Helper to encode U to (String, DynamicValue)
    // This function IS on the hot path
    def encode(u: U): (String, DynamicValue) = {
      // Use a var to store result and return after finding the matching alternative
      var result: (String, DynamicValue) = null

      // Iterate through alternatives to find which one matches
      // NOTE: This is still O(n) but unavoidable without reflection
      alternatives.foreach { alt =>
        if (result == null) {
          alt.project.lift(u).foreach { a =>
            val schema = altSchemas(alt.label)
            val dynValue = DynamicValue.fromSchemaAndValue(schema, a)
            result = (alt.label, dynValue)
          }
        }
      }

      if (result == null) {
        throw new IllegalStateException(
          s"No alternative matched for union value: $u"
        )
      }
      result
    }

    // Helper to decode (String, DynamicValue) to U
    // This function IS on the hot path
    def decode(tagged: (String, DynamicValue)): scala.util.Either[String, U] = {
      val (tag, dynValue) = tagged

      def decodeAlt[A](alt: Alt[U, A]): scala.util.Either[String, U] = {
        // Map lookup - O(log n), uses pre-compiled schema
        val schema = altSchemas(tag).asInstanceOf[ZSchema[A]]
        dynValue.toTypedValue(schema) match {
          case Left(error) =>
            Left(s"Failed to decode union alternative '$tag': $error")
          case Right(value) =>
            val u = alt.inject(value)
            Right(u)
        }
      }

      // Map lookup - O(log n)
      alternatives.find(_.label == tag) match {
        case Some(alt) => decodeAlt(alt)
        case None      => Left(s"Unknown union alternative: $tag")
      }
    }

    // Create schema for the tagged representation
    val taggedSchema: ZSchema[(String, DynamicValue)] =
      ZSchema.tuple2(ZSchema[String], ZSchema[DynamicValue])

    // Transform between tagged representation and U
    taggedSchema.transformOrFail(
      decode,
      (u: U) => Right(encode(u))
    )
  }

  override def biject[A, B](
      schema: Schema[A],
      bijection: Bijection[A, B]
  ): ZSchema[B] = {
    // Compile schema ONCE, not on every bijection access
    val memberSchema: ZSchema[A] = schema.compile(self)
    memberSchema.transform(bijection.to, bijection.from)
  }

  override def refine[A, B](
      schema: Schema[A],
      refinement: Refinement[A, B]
  ): ZSchema[B] = {
    // Compile schema ONCE, not on every refinement access
    val memberSchema: ZSchema[A] = schema.compile(self)
    memberSchema.transformOrFail(
      refinement.apply,
      b => Right(refinement.from(b))
    )
  }

  override def lazily[A](suspend: Lazy[Schema[A]]): ZSchema[A] = {
    // Lazy compilation - deferred but still compiled only once when accessed
    val zschema: Lazy[ZSchema[A]] = suspend.map(self(_))
    ZSchema.defer(zschema.value)
  }

  override def option[A](schema: Schema[A]): ZSchema[Option[A]] = {
    // Compile schema ONCE, not on every option access
    implicit val memberSchema: ZSchema[A] = schema.compile(self)
    ZSchema.option[A]
  }
}
