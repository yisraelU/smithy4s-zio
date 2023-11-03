package smithy4s.zio.prelude

import smithy4s.capability.EncoderK
import smithy4s.schema.Alt.Precompiler
import smithy4s.schema._
import smithy4s.zio.prelude.instances.all._
import smithy4s.{Schema, _}
import _root_.zio.prelude.Debug
import _root_.zio.prelude.Debug.VectorDebug
import _root_.zio.prelude.Debug.Repr
import _root_.zio.prelude.Debug.Repr.Constructor

import scala.collection.immutable.ListMap

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
    val cache: CompilationCache[Debug]
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
      case CollectionTag.VectorTag     => VectorDebug
      case CollectionTag.IndexedSeqTag => indexedSeqDebug

    }
  }

  override def map[K, V](
      shapeId: ShapeId,
      hints: Hints,
      key: Schema[K],
      value: Schema[V]
  ): Debug[Map[K, V]] = {
    implicit val keyD: Debug[K] = self(key)
    implicit val valueD: Debug[V] = self(value)
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
    ): S => (String, Repr) = {
      val debugField = self(field.schema)
      s => (field.label, debugField.debug(field.get(s)))
    }

    val functions = fields.map(f => compileField(f))
    Debug.make { s =>
      val values = functions
        .map(f => f(s))
      Constructor(
        shapeId.namespace.split(".").toList,
        shapeId.name,
        ListMap(values: _*)
      )
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
        val debugUnionInst = self(instance)
        (t: A) =>
          Constructor(
            shapeId.namespace.split(".").toList,
            shapeId.name,
            ListMap(label -> debugUnionInst.debug(t))
          )
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
    implicit val debugA: Debug[A] = self(schema)
    Debug[Option[A]]
  }
}
