package smithy4s.zio.prelude

package object instances {

  private[prelude] object all
      extends EqualsInstances
      with HashInstances
      with DebugInstances
<<<<<<< Updated upstream
=======
      with OrdInstances
      with ZSchemaInstances

  // Convenience extension for converting smithy4s schemas to ZIO schemas
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
      * import smithy4s.zio.prelude.instances._
      * import zio.schema.codec.ProtobufCodec
      *
      * val zioSchema = mySmithySchema.toZSchema
      * val codec = ProtobufCodec.protobufCodec(zioSchema)
      * }}}
      */
    def toZSchema: _root_.zio.schema.Schema[A] =
      SchemaVisitorZSchemaGen.fromSchema(schema)
  }
>>>>>>> Stashed changes

}
