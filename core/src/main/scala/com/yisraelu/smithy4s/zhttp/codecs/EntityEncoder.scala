package com.yisraelu.smithy4s.zhttp.codecs

import zio.Chunk
import zio.http._

trait EntityEncoder[-A] {

  def encode(a: A): (Headers, Body)
  final def contramap[B](f: B => A): EntityEncoder[B] = (b: B) => encode(f(b))

}
object EntityEncoder {

  def byteArrayEncoder(mediaType: MediaType): EntityEncoder[Array[Byte]] =
    (a: Array[Byte]) => {

      (
        Headers(Header.ContentType(mediaType)),
        Body.fromChunk(Chunk.fromArray(a))
      )
    }
  def empty[A]: EntityEncoder[A] = (_: A) => (Headers.empty, Body.empty)
}
