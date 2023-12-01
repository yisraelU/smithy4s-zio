package smithy4s.zio.schema

import smithy4s.schema._
import smithy4s.{Schema, _}
import _root_.zio.schema.{Schema => ZSchema}
import smithy4s.zio.schema.ZSchemaInstances.primSchemaPf
import _root_.zio.schema.CaseSet
import _root_.zio.schema.Schema.Case
import _root_.zio.schema.TypeId
object SchemaTransformer extends CachedSchemaCompiler.Impl[ZSchema] {

  protected type Aux[A] = ZSchema[A]

  def fromSchema[A](
      schema: Schema[A],
      cache: Cache
  ): ZSchema[A] = {
    schema.compile(new SchemaTransformer(cache))
  }

}

final class SchemaTransformer(
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

    def closed[A](enumV: EnumValue[E]): A => E = _ => enumV.value

    // what schema should this translate to , should we use the Smithy4s model of a schema or the ZIO model of a schema
    val caseSet: CaseSet.Aux[E] = CaseSet.apply[E](values.map { enumV =>
      {
        tag match {
          case EnumTag.ClosedStringEnum =>
            constructStringCaseSet(enumV)(closed[String](enumV))
          case EnumTag.ClosedIntEnum =>
            constructIntCaseSet(enumV)(closed[Int](enumV))
          case EnumTag.OpenStringEnum(unknown) =>
            constructStringCaseSet(enumV)(unknown)
          case EnumTag.OpenIntEnum(unknown) =>
            constructIntCaseSet(enumV)(unknown)
        }
      }
    }: _*)

    def constructStringCaseSet(enumV: EnumValue[E]) = {
      Case.apply[E, String](
        enumV.name,
        ZSchema[String],
        total(_: E).stringValue,
        _: String => E,
        (e: E) => total(e).name == enumV.name
      )
    }

    def constructIntCaseSet(enumV: EnumValue[E]) = {
      Case.apply[E, Int](
        enumV.name,
        ZSchema[Int],
        total(_: E).intValue,
        _: Int => E,
        (e: E) => total(e).name == enumV.name
      )
    }

    ZSchema.enumeration(
      TypeId.parse(shapeId.namespace),
      processed
    )
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
