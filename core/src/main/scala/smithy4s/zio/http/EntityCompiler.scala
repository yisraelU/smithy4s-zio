package smithy4s.zio.http

/*
 *  Copyright 2021-2022 Disney Streaming
 *
 *  Licensed under the Tomorrow Open Source Technology License, Version 1.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     https://disneystreaming.github.io/TOST-1.0.txt
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import smithy4s.http.{BodyPartial, CodecAPI, Metadata}
import smithy4s.Schema
import smithy4s.zio.http.codecs.{EntityDecoder, EntityEncoder}
import smithy4s.zio.http.codecs.EntityEncoder.byteArrayEncoder
import zhttp.http.MediaType

trait EntityCompiler {

  /**
   * Turns a Schema into an ZIO-Http EntityEncoder
   *
   * @param schema the value's schema
   * @return the entity encoder associated to the A value.
   */
  def compileEntityEncoder[A](schema: Schema[A]): EntityEncoder[A]

  /**
   * Turns a Schema into an zio EntityyDecoder
   *
   * @param schema the value's schema
   * @return the entity decoder associated to the A value.
   */
  def compileEntityDecoder[A](schema: Schema[A]): EntityDecoder[A]

  /**
   * Turns a Schema into an zio  BodyDecoder that only partially
   * decodes the data, expecting for decoded metadata to be provided
   * to complete the data.
   *
   * @param schema the value's schema
   * @return the entity encoder associated to the A value.
   */
  def compilePartialEntityDecoder[A](
      schema: Schema[A]
  ): EntityDecoder[BodyPartial[A]]

}

object EntityCompiler {

  def fromCodecAPI(
      codecAPI: CodecAPI
  ): EntityCompiler =
    new EntityCompiler {
      def compileEntityEncoder[A](schema: Schema[A]): EntityEncoder[A] = {
        val codecA: codecAPI.Codec[A] = codecAPI.compileCodec(schema)
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
          schema: Schema[A]
      ): EntityDecoder[A] = {
        val codecA: codecAPI.Codec[A] = codecAPI.compileCodec(schema)
        val mediaType: MediaType = mediaTypeParser(codecAPI)(codecA)
        EntityDecoder
          .byteArrayDecoder(mediaType)
          .mapOrFail(chunk =>
            codecAPI
              .decodeFromByteArray(codecA, chunk)
          )
      }

      def compilePartialEntityDecoder[A](
          schema: Schema[A]
      ): EntityDecoder[BodyPartial[A]] = {
        val codecA: codecAPI.Codec[A] = codecAPI.compileCodec(schema)
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
