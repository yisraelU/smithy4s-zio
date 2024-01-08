package smithy4s.zio.compliancetests.internals.eq

import smithy4s.{Blob, Document, Timestamp}
import cats.Eq
import cats.kernel.instances.StaticMethods
import cats.syntax.all.*
trait Smithy4sEqInstances {
  implicit def arrayEq[A: Eq]: Eq[Array[A]] = (x: Array[A], y: Array[A]) =>
    x.zip(y).forall { case (a, b) => a === b }

  implicit def indexedSeqEq[A: Eq]: Eq[IndexedSeq[A]] =
    (xs: IndexedSeq[A], ys: IndexedSeq[A]) =>
      if (xs eq ys) true
      else StaticMethods.iteratorEq(xs.iterator, ys.iterator)

  implicit val blobEq: Eq[Blob] = (x: Blob, y: Blob) => x.sameBytesAs(y)
  implicit val documentEq: Eq[Document] = Eq[String].contramap(_.show)
  implicit val timeStampEq: Eq[Timestamp] = Eq[Long].contramap(_.epochSecond)
  implicit val floatEq: Eq[Float] = (x: Float, y: Float) =>
    x == y || (x.isNaN && y.isNaN)
  implicit val doubleEq: Eq[Double] = (x: Double, y: Double) =>
    x == y || (x.isNaN && y.isNaN)

}
object Smithy4sEqInstances extends Smithy4sEqInstances
