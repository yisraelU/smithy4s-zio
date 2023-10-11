package smithy4s.zio.prelude.instances

import smithy4s.Blob
import smithy4s.kinds.PolyFunction
import smithy4s.schema.Primitive
import zio.prelude.Equal
import zio.prelude.coherent.AssociativeEqual.derive

trait EqualsInstances {

  implicit val bigIntEquals: Equal[BigInt] = Equal.default
  implicit val bigDecimalEquals: Equal[BigDecimal] = Equal.default
  implicit val blobEquals: Equal[Blob] = Equal.default
  implicit val documentEquals: Equal[smithy4s.Document] = Equal.default
  implicit val shapeIdEquals: Equal[smithy4s.ShapeId] = Equal.default
  implicit val timestampEquals: Equal[smithy4s.Timestamp] =
    Equal[Long].contramap(_.epochSecond)
  implicit val uuidEquals: Equal[java.util.UUID] = Equal.default
  val primEqualPf: PolyFunction[Primitive, Equal] = Primitive.deriving[Equal]

}

object EqualsInstances extends EqualsInstances
