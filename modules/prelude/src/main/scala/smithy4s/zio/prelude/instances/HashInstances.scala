package smithy4s.zio.prelude
package instances

import smithy4s.kinds.PolyFunction
import smithy4s.schema.Primitive
import smithy4s.zio.prelude.instances.all.blobEquals
import smithy4s.{Blob, ShapeId, Timestamp}
import zio.prelude.coherent.HashOrd.derive
import zio.prelude.{Equal, Hash}

import java.util.UUID

private[instances] trait HashInstances {

  implicit val blobHash: Hash[Blob] =
    new Hash[Blob] {
      def hash(a: Blob): Int = a.hashCode()

      def checkEqual(l: Blob, r: Blob): Boolean =
        Equal[Blob].equals(l, r)
    }
  implicit val documentHash: Hash[smithy4s.Document] =
    Hash.default
  implicit val shapeIdHash: Hash[ShapeId] = Hash.default
  implicit val timeStampHash: Hash[Timestamp] = Hash.default
  implicit val uuidHash: Hash[UUID] = Hash.default
  val primHashPf: PolyFunction[Primitive, Hash] = Primitive.deriving[Hash]

}

object HashInstances extends HashInstances
