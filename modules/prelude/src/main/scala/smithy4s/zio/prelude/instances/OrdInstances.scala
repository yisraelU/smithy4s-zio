package smithy4s.zio.prelude.instances

import smithy4s.Blob
import smithy4s.kinds.PolyFunction
import smithy4s.schema.Primitive
import zio.prelude.Ord

trait OrdInstances {

  implicit val bigIntOrd: Ord[BigInt] = Ord.default
  implicit val bigDecimalOrd: Ord[BigDecimal] = Ord.default
  implicit val blobOrd: Ord[Blob] = new Ord[Blob] {
    protected def checkCompare(l: Blob, r: Blob): zio.prelude.Ordering = {
      // Compare blobs lexicographically by their bytes
      val lBytes = l.toArray
      val rBytes = r.toArray
      val minLen = math.min(lBytes.length, rBytes.length)

      var i = 0
      while (i < minLen) {
        val cmp = java.lang.Byte.compare(lBytes(i), rBytes(i))
        if (cmp != 0) {
          return if (cmp < 0) zio.prelude.Ordering.LessThan
          else zio.prelude.Ordering.GreaterThan
        }
        i += 1
      }

      // If all bytes are equal up to minLen, compare lengths
      Ord[Int].compare(lBytes.length, rBytes.length)
    }
  }
  implicit val documentOrd: Ord[smithy4s.Document] =
    Ord[String].contramap(_.toString) // Compare by string representation
  implicit val shapeIdOrd: Ord[smithy4s.ShapeId] =
    Ord[String].contramap(_.toString)
  implicit val timestampOrd: Ord[smithy4s.Timestamp] =
    Ord[Long].contramap(_.epochSecond)
  implicit val uuidOrd: Ord[java.util.UUID] =
    Ord[String].contramap(_.toString)

  val primOrdPf: PolyFunction[Primitive, Ord] = Primitive.deriving[Ord]

}

object OrdInstances extends OrdInstances
