package smithy4s.zio

import _root_.zio.schema.{Schema => ZSchema}

package object schema {

  /** Convenience extension for converting smithy4s schemas to ZIO schemas.
    *
    * Example:
    * {{{
    * import smithy4s.zio.schema._
    *
    * val zioSchema = mySmithySchema.toZSchema
    * }}}
    */
  implicit class Smithy4sSchemaOps[A](private val schema: smithy4s.Schema[A])
      extends AnyVal {

    /** Convert this smithy4s schema to a ZIO Schema.
      *
      * This enables access to the ZIO Schema ecosystem:
      * - Codecs (Protobuf, Avro, JSON, Thrift, MessagePack)
      * - Validation
      * - Schema migrations
      * - Diffing and pretty printing
      *
      * Example:
      * {{{
      * import smithy4s.zio.schema._
      * import zio.schema.codec.ProtobufCodec
      *
      * val zioSchema = mySmithySchema.toZSchema
      * val codec = ProtobufCodec.protobufCodec(zioSchema)
      * }}}
      *
      * IMPORTANT: The schema compilation happens ONCE when you call `.toZSchema`.
      * The result should be stored in a `val` to avoid recompilation:
      *
      * {{{
      * // GOOD: Compiled once
      * val personSchema = Person.smithy4sSchema.toZSchema
      *
      * // BAD: Would recompile on every access
      * def personSchema = Person.smithy4sSchema.toZSchema
      * }}}
      */
    def toZSchema: ZSchema[A] =
      SchemaVisitorZSchemaGen.fromSchema(schema)
  }

}
