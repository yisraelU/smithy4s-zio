package smithy4s.zio.prelude

import smithy4s.schema._
import smithy4s.zio.prelude.instances.ZSchemaInstances.primSchemaPf
import smithy4s.{Schema, _}
import _root_.zio.schema.{Schema => ZSchema}

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
    primSchemaPf(tag)
  }

  override def collection[C[_], A](
      shapeId: ShapeId,
      hints: Hints,
      tag: CollectionTag[C],
      member: Schema[A]
  ): ZSchema[C[A]] = {
    implicit val memberSchema: ZSchema[A] = member.compile(self)
    tag match {
      case CollectionTag.ListTag   => ZSchema.list[A]
      case CollectionTag.SetTag    => ZSchema.set[A]
      case CollectionTag.VectorTag => ZSchema.vector[A]
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

    ???
  }

  override def struct[S](
      shapeId: ShapeId,
      hints: Hints,
      fields: Vector[Field[S, _]],
      make: IndexedSeq[Any] => S
  ): ZSchema[S] = ???

  override def union[U](
      shapeId: ShapeId,
      hints: Hints,
      alternatives: Vector[Alt[U, _]],
      dispatch: Alt.Dispatcher[U]
  ): ZSchema[U] = ???

  override def biject[A, B](
      schema: Schema[A],
      bijection: Bijection[A, B]
  ): ZSchema[B] = {
    val memberSchema: ZSchema[A] = schema.compile(self)
    memberSchema.transform(bijection.to, bijection.from)
  }

  override def refine[A, B](
      schema: Schema[A],
      refinement: Refinement[A, B]
  ): ZSchema[B] = {
    val memberSchema: ZSchema[A] = schema.compile(self)
    memberSchema.transformOrFail(
      refinement.apply,
      b => Right(refinement.from(b))
    )
  }

  override def lazily[A](suspend: Lazy[Schema[A]]): ZSchema[A] = {
    val zschema: Lazy[ZSchema[A]] = suspend.map(self(_))
    ZSchema.defer(zschema.value)

  }

  override def option[A](schema: Schema[A]): ZSchema[Option[A]] = {
    implicit val memberSchema: ZSchema[A] = schema.compile(self)
    ZSchema.option[A]
  }
}
