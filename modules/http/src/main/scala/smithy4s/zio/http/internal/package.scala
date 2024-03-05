package smithy4s.zio.http

import cats.effect.SyncIO
import org.typelevel.vault.Key
import smithy4s.Blob
import smithy4s.capability.MonadThrowLike
import smithy4s.http.{
  CaseInsensitive,
  HttpMethod,
  PathParams,
  HttpRequest as Smithy4sHttpRequest,
  HttpResponse as Smithy4sHttpResponse,
  HttpUri as Smithy4sHttpUri,
  HttpUriScheme as Smithy4sHttpUriScheme
}
import zio.http.*
import zio.stream.ZStream
import zio.{Chunk, IO, Task, ZIO}

package object internal {
  def zioMonadThrowLike[R]: MonadThrowLike[ZIO[R, Throwable, *]] =
    new MonadThrowLike[ZIO[R, Throwable, *]] {
      override def flatMap[A, B](fa: ZIO[R, Throwable, A])(
          f: A => ZIO[R, Throwable, B]
      ): ZIO[R, Throwable, B] =
        fa.flatMap(f)

      override def raiseError[A](e: Throwable): ZIO[R, Throwable, A] =
        ZIO.die(e)

      override def pure[A](a: A): ZIO[R, Throwable, A] = ZIO.succeed(a)

      // we have already handled throwables via using a Response
      override def handleErrorWith[A](fa: ZIO[R, Throwable, A])(
          f: Throwable => ZIO[R, Throwable, A]
      ): ZIO[R, Throwable, A] = fa.catchAllDefect(f)

      override def zipMapAll[A](seq: IndexedSeq[ZIO[R, Throwable, Any]])(
          f: IndexedSeq[Any] => A
      ): ZIO[R, Throwable, A] = ZIO.collectAll(seq).map(f)
    }

  implicit class EffectOps[+A, +E](private val a: A) extends AnyVal {
    def pure: IO[E, A] = ZIO.succeed(a)
    def fail[E1 >: E](e: E1): IO[E1, A] = ZIO.fail(e)
  }

  def toSmithy4sHttpRequest(req: Request): Task[Smithy4sHttpRequest[Blob]] = {
    val (newReq, pathParams) = lookupPathParams(req)
    val uri = toSmithy4sHttpUri(newReq.url, pathParams)
    val headers = getHeaders(newReq.headers)
    val method = toSmithy4sHttpMethod(newReq.method)
    collectBytes(newReq.body).map { blob =>
      Smithy4sHttpRequest(method, uri, headers, blob)
    }
  }

  def fromSmithy4sHttpRequest(req: Smithy4sHttpRequest[Blob]): Request = {
    val method = fromSmithy4sHttpMethod(req.method)
    val headers: Headers = toHeaders(req.headers)
    val updatedHeaders = req.body.size match {
      case 0 => headers
      case contentLength =>
        headers.addHeader("Content-Length", contentLength.toString)
    }
    Request(
      version = Version.`HTTP/1.1`,
      method,
      fromSmithy4sHttpUri(req.uri),
      updatedHeaders,
      Body.fromStream(toStream(req.body)),
      remoteAddress = Option.empty
    )
  }

  def toSmithy4sHttpUri(
      url: URL,
      pathParams: Option[PathParams] = None
  ): Smithy4sHttpUri = {
    val uriScheme = url.scheme match {
      case Some(scheme) =>
        scheme match {
          case Scheme.HTTP  => Smithy4sHttpUriScheme.Http
          case Scheme.HTTPS => Smithy4sHttpUriScheme.Https
          case Scheme.WS =>
            throw new UnsupportedOperationException("Websocket not supported")
          case Scheme.WSS =>
            throw new UnsupportedOperationException(
              "Secure Websocket not supported"
            )
          case Scheme.Custom(scheme) =>
            throw new UnsupportedOperationException(
              s"custom scheme $scheme is not supported"
            )
        }
      case _ => Smithy4sHttpUriScheme.Http
    }

    Smithy4sHttpUri(
      uriScheme,
      url.host.getOrElse("localhost"),
      url.port,
      // we fake decoding as , we wish to have uri decoding not url decoding
      url.path.segments.map(s => s.replace("+", "%2b")).map(URICodec.decode),
      getQueryParams(url),
      pathParams
    )
  }

  def fromSmithy4sHttpResponse(res: Smithy4sHttpResponse[Blob]): Response = {
    val status = Status.fromInt(res.statusCode) match {
      case Some(value) => value
      case None =>
        throw new RuntimeException(s"Invalid status code ${res.statusCode}")
    }

    val headers: Headers = toHeaders(res.headers)
    val updatedHeaders: Headers = {
      val contentLength = res.body.size
      if (contentLength <= 0) headers
      else headers.addHeader("Content-Length", contentLength.toString)
    }
    Response(
      status,
      headers = updatedHeaders,
      body = Body.fromStream(toStream(res.body))
    )
  }

  def toSmithy4sHttpResponse(
      res: Response
  ): IO[Throwable, Smithy4sHttpResponse[Blob]] =
    collectBytes(res.body).map { blob =>
      val headers = res.headers.headers
        .map(h => CaseInsensitive(h.headerName) -> Seq(h.renderedValue))
        .toMap
      Smithy4sHttpResponse(res.status.code, headers, blob)
    }

  private def fromSmithy4sHttpUri(uri: Smithy4sHttpUri): URL = {
    val path = Path(uri.path.map(URICodec.encode).mkString("/")).addLeadingSlash
    val scheme = uri.scheme match {
      case Smithy4sHttpUriScheme.Https => Scheme.HTTPS
      case _                           => Scheme.HTTP
    }
    val queryParams: Map[String, Chunk[String]] = uri.queryParams.map {
      case (str, value) => (str, Chunk.fromIterable(value))
    }
    val location = URL.Location.Absolute(
      scheme = scheme,
      host = uri.host,
      originalPort = uri.port
    )

    URL(
      path = path,
      kind = location,
      queryParams = QueryParams.apply(queryParams),
      fragment = None
    )
  }

  /**
   * A vault key that is used to store extracted path-parameters into request during
   * the routing logic.
   *
   * The http path matching logic extracts the relevant segment of the URI in order
   * to verify that a request corresponds to an endpoint. This information MUST be stored
   * in the request before any decoding of metadata is attempted, as it'll fail otherwise.
   */

  private[smithy4s] def fromSmithy4sHttpMethod(
      method: HttpMethod
  ): Method =
    method match {
      case HttpMethod.PUT      => Method.PUT
      case HttpMethod.POST     => Method.POST
      case HttpMethod.DELETE   => Method.DELETE
      case HttpMethod.GET      => Method.GET
      case HttpMethod.PATCH    => Method.PATCH
      case HttpMethod.OTHER(v) => Method.fromString(v)
    }

  private[smithy4s] def toSmithy4sHttpMethod(method: Method): HttpMethod =
    method match {
      case Method.PUT    => HttpMethod.PUT
      case Method.POST   => HttpMethod.POST
      case Method.DELETE => HttpMethod.DELETE
      case Method.GET    => HttpMethod.GET
      case Method.PATCH  => HttpMethod.PATCH
      case other         => HttpMethod.OTHER(other.name)
    }

  private[smithy4s] def getQueryParams(
      uri: URL
  ): Map[String, List[String]] =
    uri.queryParams.map
      .collect {
        case (name, value) if value.isEmpty => name -> List("true")
        case (name, values)                 => name -> values.toList
      }

  private def collectBytes(body: Body): Task[Blob] =
    body.asStream.chunks.runCollect
      .map(_.flatten)
      .map(chunk => Blob(chunk.toArray))

  private def toStream(
      blob: Blob
  ): ZStream[Any, Nothing, Byte] =
    ZStream.fromChunk(Chunk.fromArray(blob.toArray))

  private def toHeaders(mp: Map[CaseInsensitive, Seq[String]]): Headers = {
    Headers {
      mp.flatMap { case (k, v) =>
        v.map(vv => Header.Custom(k.value, vv))
      }.toList
    }
  }

  private[smithy4s] def getHeaders(
      req: Headers
  ): Map[CaseInsensitive, List[String]] =
    req.headers.toList.groupBy(_.headerName).map { case (k, v) =>
      (CaseInsensitive(k), v.map(_.renderedValue))
    }

  private val pathParamsKey: String =
    Key.newKey[SyncIO, PathParams].unsafeRunSync().hashCode().toString

  private def serializePathParams(pathParams: PathParams): String = {
    pathParams
      .map { case (key, value) => s"$key=${URICodec.encode(value)}" }
      .mkString("&")
  }

  // string is already decoded from URL Encoding , so we need to handle literals which may have been escaped
  private def deserializePathParams(pathParamsString: String): PathParams = {
    pathParamsString
      .split("&")
      .filterNot(_.isEmpty)
      .map { param =>
        {
          param.split("=", 2) match {
            case Array(key, value) => key -> URICodec.decode(value)
            case Array(k)          => (k, "")
            case _ =>
              throw new Exception(
                s"Invalid path params string: $pathParamsString"
              )
          }
        }
      }
      .toMap

  }
  private def lookupPathParams(req: Request): (Request, Option[PathParams]) = {
    val pathParamsString = req.headers.get(pathParamsKey)
    (
      req.removeHeader(pathParamsKey),
      pathParamsString.map(deserializePathParams)
    )
  }

  def tagRequest(req: Request, pathParams: PathParams): Request = {
    val serializedPathParams = serializePathParams(pathParams)
    req.addHeader(Header.Custom(pathParamsKey, serializedPathParams))
  }
}
