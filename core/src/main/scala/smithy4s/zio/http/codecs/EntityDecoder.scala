package smithy4s.zio.http.codecs

import zhttp.http.{Body, Headers, MediaType}
import zio.{Task, ZIO}

trait EntityDecoder[+A] {

  def decode(headers: Headers, body: Body): Task[A]

  def map[B](f: A => B): EntityDecoder[B] = (headers: Headers, body: Body) =>
    decode(headers, body).map(f)

  def mapOrFail[B](f: A => Either[Throwable, B]): EntityDecoder[B] =
    (headers: Headers, body: Body) => {
      decode(headers, body).map(f).absolve
    }

}

object EntityDecoder {

  def empty: EntityDecoder[Unit] = (_: Headers, _: Body) => zio.ZIO.succeed(())

  def byteArrayDecoder(mediaType: MediaType): EntityDecoder[Array[Byte]] =
    (_: Headers, body: Body) => {
      body.asArray
    }
}
