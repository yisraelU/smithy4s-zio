package smithy4s.zio.compliancetests.internals

import smithy4s.Document.DNull
import smithy4s.schema._
import smithy4s.schema.Primitive._
import smithy4s.{Schema, _}
import cats.Id
import java.util.UUID

private[compliancetests] object DefaultSchemaVisitor extends SchemaVisitor[Id] {
  self =>

  override def primitive[P](
      shapeId: ShapeId,
      hints: Hints,
      tag: Primitive[P]
  ): Id[P] = tag match {
    case PFloat      => 0: Float
    case PBigDecimal => 0: BigDecimal
    case PBigInt     => 0: BigInt
    case PBlob       => Blob(Array.emptyByteArray)
    case PDocument   => DNull
    case PByte       => 0: Byte
    case PInt        => 0
    case PShort      => 0: Short
    case PString     => ""
    case PLong       => 0: Long
    case PDouble     => 0: Double
    case PBoolean    => true
    case PTimestamp  => Timestamp(0L, 0)
    case PUUID       => new UUID(0, 0)
  }

  override def collection[C[_], A](
      shapeId: ShapeId,
      hints: Hints,
      tag: CollectionTag[C],
      member: Schema[A]
  ): Id[C[A]] = tag.empty

  override def map[K, V](
      shapeId: ShapeId,
      hints: Hints,
      key: Schema[K],
      value: Schema[V]
  ): Id[Map[K, V]] = Map.empty

  override def enumeration[E](
      shapeId: ShapeId,
      hints: Hints,
      tag: EnumTag[E],
      values: List[EnumValue[E]],
      total: E => EnumValue[E]
  ): Id[E] = values.head.value

  override def struct[S](
      shapeId: ShapeId,
      hints: Hints,
      fields: Vector[Field[S, _]],
      make: IndexedSeq[Any] => S
  ): Id[S] = make(fields.map(_.schema.compile(self)))

  override def union[U](
      shapeId: ShapeId,
      hints: Hints,
      alternatives: Vector[Alt[U, _]],
      dispatch: Alt.Dispatcher[U]
  ): Id[U] = {
    def processAlt[A](alt: Alt[U, A]) = alt.inject(apply(alt.schema))
    processAlt(alternatives.head)
  }

  override def biject[A, B](
      schema: Schema[A],
      bijection: Bijection[A, B]
  ): Id[B] = bijection(apply(schema))

  override def refine[A, B](
      schema: Schema[A],
      refinement: Refinement[A, B]
  ): Id[B] = refinement.unsafe(apply(schema))

  override def lazily[A](suspend: Lazy[Schema[A]]): Id[A] = {
    suspend.map(apply).value
  }

  override def option[A](schema: Schema[A]): Id[Option[A]] = None

}
