package smithy4s.zio.prelude.instances

import smithy4s.{Blob, Document, ShapeId, Timestamp}
import smithy4s.kinds.PolyFunction
import smithy4s.schema.Primitive
import zio.prelude.{Debug, DebugOps}
import zio.prelude.Debug._
import zio.prelude.Debug.Repr

import java.util.UUID

trait DebugInstances {
  private final def ToString[A]: Debug[A] =
    Debug.make(a => Repr.String(a.toString))

  implicit val sIdDebug: Debug[ShapeId] = ToString
  implicit val blobDebug: Debug[Blob] = (a: Blob) =>
    Repr.String(a.toBase64String)
  implicit val documentDebug: Debug[Document] = ToString
  implicit val tsDebug: Debug[Timestamp] = ToString
  implicit val uuidDebug: Debug[UUID] = ToString
  implicit def setDebug[A: Debug]: Debug[Set[A]] = {
    Debug.make { set =>
      Repr.VConstructor(List("scala"), "Set", set.toList.map(_.debug))
    }
  }
  implicit def indexedSeqDebug[A: Debug]: Debug[IndexedSeq[A]] = {
    Debug.make { seq =>
      Repr.VConstructor(List("scala"), "IndexedSeq", seq.toList.map(_.debug))
    }
  }
  val primDebugPf: PolyFunction[Primitive, Debug] =
    Primitive.deriving[Debug]
}

object DebugInstances extends DebugInstances
