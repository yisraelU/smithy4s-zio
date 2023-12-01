package smithy4s.zio.http

import smithy4s.Blob
import smithy4s.capability.MonadThrowLike
import smithy4s.http.{
  CaseInsensitive,
  HttpMethod,
  PathParams,
  HttpRequest => Smithy4sHttpRequest,
  HttpResponse => Smithy4sHttpResponse,
  HttpUri => Smithy4sHttpUri,
  HttpUriScheme => Smithy4sHttpUriScheme
}
import smithy4s.zio.http.internal.ZHttpToSmithy4sClient.ResourcefulTask
import zio.http._
import zio.stream.ZStream
import zio.{Chunk, IO, Task, ZIO}

package object internal {

  implicit val zioMonadThrowLike: MonadThrowLike[ResourcefulTask] =
    new MonadThrowLike[ResourcefulTask] {
      override def flatMap[A, B](fa: ResourcefulTask[A])(
          f: A => ResourcefulTask[B]
      ): ResourcefulTask[B] =
        fa.flatMap(f)

      override def raiseError[A](e: Throwable): Task[A] = ZIO.fail(e)

      override def pure[A](a: A): Task[A] = ZIO.succeed(a)

      override def handleErrorWith[A](fa: ResourcefulTask[A])(
          f: Throwable => ResourcefulTask[A]
      ): ResourcefulTask[A] = fa.catchAll(f)

      override def zipMapAll[A](seq: IndexedSeq[ResourcefulTask[Any]])(
          f: IndexedSeq[Any] => A
      ): ResourcefulTask[A] = ZIO.collectAll(seq).map(f)
    }

  implicit class EffectOps[+A](val a: A) extends AnyVal {
    def pure: Task[A] = ZIO.succeed(a)
    def fail[E](e: E): IO[E, A] = ZIO.fail(e)
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
      uri: URL,
      pathParams: Option[PathParams] = None
  ): Smithy4sHttpUri = {
    val uriScheme = uri.scheme match {
      case Some(scheme) =>
        scheme match {
          case Scheme.HTTP  => Smithy4sHttpUriScheme.Http
          case Scheme.HTTPS => Smithy4sHttpUriScheme.Https
          case Scheme.WS =>
            throw new UnsupportedOperationException("Websocket not supported")
          case Scheme.WSS =>
            throw new UnsupportedOperationException("Websocket not supported")
        }
      case _ => Smithy4sHttpUriScheme.Http
    }

    Smithy4sHttpUri(
      uriScheme,
      uri.host.getOrElse("localhost"),
      uri.port,
      uri.path.segments,
      getQueryParams(uri),
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

  def toSmithy4sHttpResponse(res: Response): Task[Smithy4sHttpResponse[Blob]] =
    collectBytes(res.body).map { blob =>
      val headers = res.headers.headers
        .map(h => CaseInsensitive(h.headerName) -> Seq(h.renderedValue))
        .toMap
      Smithy4sHttpResponse(res.status.code, headers, blob)
    }

  def fromSmithy4sHttpUri(uri: Smithy4sHttpUri): URL = {
    val path = Path(uri.path.mkString("/")).addLeadingSlash
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
      port = uri.port.getOrElse(80)
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
}
