package smithy4s.zio.schema

import smithy4s.{Blob, Document, Timestamp}
import smithy4s.kinds.PolyFunction
import smithy4s.schema.Primitive
import zio.Chunk
import zio.schema.{Schema => ZSchema}

object ZSchemaInstances {

  implicit val zBlob: ZSchema[Blob] = ZSchema
    .chunk[Byte]
    .transform(
      chunk => Blob(chunk.toArray),
      blob => Chunk.fromArray(blob.toArray)
    )

  implicit val zDocument: ZSchema[smithy4s.Document] =
    ZSchema[String].transform(
      Document.fromString,
      _.toString()
    )

  implicit val zTimestamp: ZSchema[smithy4s.Timestamp] =
    ZSchema[Long].transform(
      Timestamp.fromEpochSecond,
      _.epochSecond
    )

  val primSchemaPf: PolyFunction[Primitive, ZSchema] =
    Primitive.deriving[ZSchema]

}
