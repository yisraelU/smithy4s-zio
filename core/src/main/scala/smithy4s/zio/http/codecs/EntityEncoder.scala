package smithy4s.zio.http.codecs

import zhttp.http.{Body, Headers, MediaType}
import zio.Chunk

trait EntityEncoder[-A] {

  def encode(a: A): (Headers, Body)

  def contramap[B](f: B => A): EntityEncoder[B] = (b: B) => encode(f(b))
}
object EntityEncoder {

  def byteArrayEncoder(mediaType: MediaType): EntityEncoder[Array[Byte]] =
    (a: Array[Byte]) => {

      (
        Headers.contentType(mediaType.fullType),
        Body.fromChunk(Chunk.fromArray(a))
      )
    }
  def empty[A]: EntityEncoder[A] = (_: A) => (Headers.empty, Body.empty)
}
