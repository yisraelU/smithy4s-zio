package com.yisraelu.smithy4s.zhttp

import com.yisraelu.smithy4s.zhttp.codecs.{EntityDecoder, EntityEncoder}
import smithy4s.http.{BodyPartial, CodecAPI, Metadata}
import smithy4s.Schema
import com.yisraelu.smithy4s.zhttp.codecs.EntityEncoder.byteArrayEncoder
import zio.http.MediaType

trait EntityCompiler {

  type Cache

  def createCache(): Cache

  /**
   * Turns a Schema into an ZIO-Http EntityEncoder
   *
   * @param schema the value's schema
   * @param cache a cache that can be used to avoid recompiling encoders
   * @return the entity encoder associated to the A value.
   */
  def compileEntityEncoder[A](schema: Schema[A], cache: Cache): EntityEncoder[A]

  /**
   * Turns a Schema into an ZIO-Http EntityyDecoder
   *
   * @param schema the value's schema
   * @param cache a cache that can be used to avoid recompiling encoders
   * @return the entity decoder associated to the A value.
   */
  def compileEntityDecoder[A](schema: Schema[A], cache: Cache): EntityDecoder[A]

  /**
   * Turns a Schema into an ZIO-Http  BodyDecoder that only partially
   * decodes the data, expecting for decoded metadata to be provided
   * to complete the data.
   *
   * @param schema the value's schema
   * @param cache a cache that can be used to avoid recompiling encoders
   * @return the entity encoder associated to the A value.
   */
  def compilePartialEntityDecoder[A](
      schema: Schema[A],
      cache: Cache
  ): EntityDecoder[BodyPartial[A]]

}

object EntityCompiler {

  def fromCodecAPI(
      codecAPI: CodecAPI
  ): EntityCompiler =
    new EntityCompiler {
      type Cache = codecAPI.Cache
      def createCache(): Cache = codecAPI.createCache()
      def compileEntityEncoder[A](
          schema: Schema[A],
          cache: Cache
      ): EntityEncoder[A] = {
        val codecA = codecAPI.compileCodec(schema, cache)
        val mediaType: MediaType = mediaTypeParser(codecAPI)(codecA)
        val expectBody = Metadata.PartialDecoder
          .fromSchema(schema)
          .total
          .isEmpty // expect body if metadata decoder is not total
        if (expectBody) {
          byteArrayEncoder(mediaType)
            .contramap(a => codecAPI.writeToArray(codecA, a))
        } else {
          EntityEncoder.empty
        }
      }

      def compileEntityDecoder[A](
          schema: Schema[A],
          cache: Cache
      ): EntityDecoder[A] = {
        val codecA = codecAPI.compileCodec(schema, cache)
        val mediaType: MediaType = mediaTypeParser(codecAPI)(codecA)
        EntityDecoder
          .byteArrayDecoder(mediaType)
          .mapOrFail(chunk =>
            codecAPI
              .decodeFromByteArray(codecA, chunk)
          )
      }

      def compilePartialEntityDecoder[A](
          schema: Schema[A],
          cache: Cache
      ): EntityDecoder[BodyPartial[A]] = {
        val codecA = codecAPI.compileCodec(schema, cache)
        val mediaType: MediaType = mediaTypeParser(codecAPI)(codecA)
        EntityDecoder
          .byteArrayDecoder(mediaType)
          .mapOrFail(chunk =>
            codecAPI
              .decodeFromByteArrayPartial(codecA, chunk)
          )
      }

    }

}
