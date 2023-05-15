package com.yisraelu.smithy4s.zhttp.codecs

import zio.Task
import zio.http._

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
