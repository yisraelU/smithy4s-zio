

package com.yisraelu.smithy4s

import com.yisraelu.smithy4s.zhttp.codecs.EntityEncoder
import smithy4s.http.{CaseInsensitive, CodecAPI, HttpMethod}
import zio.http._
import zio.http.Method._

package object zhttp {
  implicit final class ServiceOps[Alg[_[_, _, _, _, _]]](
      private[this] val service: smithy4s.Service[Alg]
  ) {

    def restJson: SimpleRestJsonBuilder.ServiceBuilder[Alg] =
      SimpleRestJsonBuilder(service)

  }
  def toZhttpMethod(method: HttpMethod): Method = {
    method match {
      case HttpMethod.PUT          => PUT
      case HttpMethod.POST         => POST
      case HttpMethod.DELETE       => DELETE
      case HttpMethod.GET          => GET
      case HttpMethod.PATCH        => PATCH
      case HttpMethod.OTHER(value) => Method.fromString(value)
    }
  }

  def toHeaders(mp: Map[CaseInsensitive, Seq[String]]): Headers = {
    Headers {
      mp.flatMap { case (k, v) =>
        v.map(vv => Headers(k.value, vv))
      }.toList:_*
    }
  }

  private[smithy4s] def getHeaders(
      req: Headers
  ): Map[CaseInsensitive, List[String]] =
    req.headers.toList.groupBy(_.headerName).map { case (k, v) =>
      (CaseInsensitive(k), v.map(_.renderedValue))
    }

  private[smithy4s] def getFirstHeader(
      headers: Headers,
      s: String
  ): Option[String] =
    headers.toList.find(_.headerName == s).map(_.renderedValue)

  def mediaTypeParser[A](codecAPI: CodecAPI): codecAPI.Codec[A] => MediaType = {
    c =>
      MediaType
        .forContentType(codecAPI.mediaType(c).value)
        .getOrElse(throw new RuntimeException("media type not supported"))
  }

  def collectFirstSome[A, B](list: List[A])(f: A => Option[B]): Option[B] = {
    list.map(f).collectFirst { case Some(value) =>
      value
    }
  }

  implicit class ZHttpResponse(val response: Response) extends AnyVal {
    def withEntity[A](
        a: A
    )(implicit entityEncoder: EntityEncoder[A]): Response = {
      response.copy(body = entityEncoder.encode(a)._2)
    }
  }
  implicit class PathOps(private val path: Path) extends AnyVal {
    def toArray: Array[String] = path.encode.split("/").tail
  }

}
