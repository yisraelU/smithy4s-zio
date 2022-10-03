package smithy4s.zio

import smithy4s.http.{CaseInsensitive, CodecAPI, HttpMethod}
import smithy4s.http.CodecAPI.Codec
import smithy4s.zio.http.codecs.EntityEncoder
import zhttp.http.{Body, Headers, MediaType, Method, Request, Response}
import zhttp.service.Client

package object http {

  implicit final class ServiceOps[Alg[_[_, _, _, _, _]], Op[_, _, _, _, _]](private[this] val serviceProvider: smithy4s.Service.Provider[Alg, Op]) {

    def simpleRestJson: SimpleRestJsonBuilder.ServiceBuilder[Alg, Op] =
      SimpleRestJsonBuilder(serviceProvider.service)

  }
  def toZhttpMethod(method: HttpMethod): Method = {
    method match {
      case HttpMethod.PUT    => Method.PUT
      case HttpMethod.POST   => Method.POST
      case HttpMethod.DELETE => Method.DELETE
      case HttpMethod.GET    => Method.GET
      case HttpMethod.PATCH  => Method.PATCH
    }
  }

  def toHeaders(mp: Map[CaseInsensitive, Seq[String]]): Headers = {
    Headers {
      mp.flatMap { case (k, v) =>
        v.map(vv => (k.value, vv))
      }.toList
    }
  }

  private[smithy4s] def getHeaders(
      req: Headers
  ): Map[CaseInsensitive, List[String]] =
    req.headers.toList.groupBy(_._1).map { case (k, v) =>
      (CaseInsensitive(k), v.map(_._2))
    }

  private[smithy4s] def getFirstHeader(
      headers: Headers,
      s: String
  ): Option[String] =
    headers.toList.find(_._1 == s).map(_._2)

  def mediaTypeParser[A](codecAPI: CodecAPI): codecAPI.Codec[A] => MediaType = {
    c =>
      MediaType
        .forContentType(codecAPI.mediaType(c).value)
        .getOrElse(throw new RuntimeException("media type not supported"))
  }
  
  def  collectFirstSome[A,B](list: List[A])(f: A =>Option[B] ):Option[B] = {
    list.map(f).collectFirst {
      case Some(value) => value
    }
  }

  implicit class ZHttpResponse(val response: Response) extends AnyVal {
    def withEntity[A](
        a: A
    )(implicit entityEncoder: EntityEncoder[A]): Response = {
      response.copy(body = entityEncoder.encode(a)._2)
    }
  }
}
