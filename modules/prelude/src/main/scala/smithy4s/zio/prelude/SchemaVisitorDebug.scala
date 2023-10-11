package smithy4s.zio.prelude

import smithy4s.capability.EncoderK
import smithy4s.schema.Alt.Precompiler
import smithy4s.{Bijection, Hints, Lazy, Refinement, ShapeId}
import smithy4s.zio.prelude.instances.all._
import smithy4s.schema.{
  Alt,
  CachedSchemaCompiler,
  CollectionTag,
  CompilationCache,
  EnumTag,
  EnumValue,
  Field,
  Primitive,
  Schema,
  SchemaVisitor
}
import zio.prelude.{Debug, DebugOps}
import zio.prelude.Debug.{Renderer, Repr}

object SchemaVisitorDebug extends CachedSchemaCompiler.Impl[Debug] {
  protected type Aux[A] = Debug[A]
  def fromSchema[A](
      schema: Schema[A],
      cache: Cache
  ): Debug[A] = {
    schema.compile(new SchemaVisitorDebug(cache))
  }
}

final class SchemaVisitorDebug(
    val cache: CompilationCache[Debug],
    val renderer: Renderer = Renderer.Simple
) extends SchemaVisitor.Cached[Debug] {
  self =>
  override def primitive[P](
      shapeId: ShapeId,
      hints: Hints,
      tag: Primitive[P]
  ): Debug[P] = primDebugPf(tag)

  override def collection[C[_], A](
      shapeId: ShapeId,
      hints: Hints,
      tag: CollectionTag[C],
      member: Schema[A]
  ): Debug[C[A]] = {
    implicit val memberDebug: Debug[A] = self(member)
    tag match {
      case CollectionTag.ListTag       => Debug[List[A]]
      case CollectionTag.SetTag        => setDebug
      case CollectionTag.VectorTag     => Debug[Vector[A]]
      case CollectionTag.IndexedSeqTag => indexedSeqDebug

    }
  }

  override def map[K, V](
      shapeId: ShapeId,
      hints: Hints,
      key: Schema[K],
      value: Schema[V]
  ): Debug[Map[K, V]] = {
    implicit val keyD = self(key)
    implicit val valueD = self(value)
    Debug[Map[K, V]]
  }

  override def enumeration[E](
      shapeId: ShapeId,
      hints: Hints,
      tag: EnumTag[E],
      values: List[EnumValue[E]],
      total: E => EnumValue[E]
  ): Debug[E] = Debug.make(e => Repr.String(total(e).stringValue))

  override def struct[S](
      shapeId: ShapeId,
      hints: Hints,
      fields: Vector[Field[S, _]],
      make: IndexedSeq[Any] => S
  ): Debug[S] = {
    def compileField[A](
        field: Field[S, A]
    ): S => String = {
      val debugField = self(field.schema)
      s => s"${field.label} = ${debugField.debug(field.get(s))}"
    }

    val functions = fields.map(f => compileField(f))
    Debug.make { s =>
      val values = functions
        .map(f => f(s))
        .map { case (value) => s"$value" }
        .mkString("(", ", ", ")")
      s"${shapeId.name}$values"
    }
  }

  override def union[U](
      shapeId: ShapeId,
      hints: Hints,
      alternatives: Vector[Alt[U, _]],
      dispatch: Alt.Dispatcher[U]
  ): Debug[U] = {
    val precomputed: Precompiler[Debug] = new Precompiler[Debug] {
      override def apply[A](label: String, instance: Schema[A]): Debug[A] = {
        val showUnion = self(instance)
        (t: A) => s"${shapeId.name}($label = ${showUnion.debug(t).render})"
      }
    }
    implicit val encoderKShow: EncoderK[Debug, Repr] =
      new EncoderK[Debug, Repr] {
        override def apply[A](fa: Debug[A], a: A): Repr = fa.debug(a)

        override def absorb[A](f: A => Repr): Debug[A] = Debug.make(f)
      }
    dispatch.compile(precomputed)
  }

  override def biject[A, B](
      schema: Schema[A],
      bijection: Bijection[A, B]
  ): Debug[B] = {
    val debugA = self(schema)
    Debug.make(b => debugA.debug(bijection.from(b)))
  }

  override def refine[A, B](
      schema: Schema[A],
      refinement: Refinement[A, B]
  ): Debug[B] = {
    val debugA = self(schema)
    Debug.make(b => debugA.debug(refinement.from(b)))
  }

  override def lazily[A](suspend: Lazy[Schema[A]]): Debug[A] = {
    val ss = suspend.map {
      self(_)
    }
    a => ss.value.debug(a)
  }

  override def option[A](schema: Schema[A]): Debug[Option[A]] = {
    implicit val debugA = self(schema)
    Debug[Option[A]]
  }
}
