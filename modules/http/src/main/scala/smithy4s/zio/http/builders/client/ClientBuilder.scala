package smithy4s.zio.http.builders.client

import smithy4s.client.UnaryClientCompiler
import smithy4s.zio.http.internal.{ZHttpToSmithy4sClient, zioMonadThrowLike}
import smithy4s.zio.http.{
  ClientEndpointMiddleware,
  ResourcefulTask,
  SimpleProtocolCodecs
}
import smithy4s.{Endpoint, ShapeTag, UnsupportedProtocolError, checkProtocol}
import zio.http.{Client, Response, URL}
import zio.{IO, Scope, ZIO}

class ClientBuilder[
    Alg[_[_, _, _, _, _]],
    P
] private[http] (
    simpleProtocolCodecs: SimpleProtocolCodecs,
    client: Client,
    val service: smithy4s.Service[Alg],
    url: URL = URL
      .decode("http://localhost:8080")
      .getOrElse(throw new Exception("Invalid URL")),
    middleware: ClientEndpointMiddleware = Endpoint.Middleware.noop[Client]
)(implicit protocolTag: ShapeTag[P]) {

  def uri(uri: URL): ClientBuilder[Alg, P] =
    new ClientBuilder[Alg, P](
      simpleProtocolCodecs,
      this.client,
      this.service,
      uri,
      this.middleware
    )

  def middleware(
      mid: ClientEndpointMiddleware
  ): ClientBuilder[Alg, P] =
    new ClientBuilder[Alg, P](
      simpleProtocolCodecs,
      this.client,
      this.service,
      this.url,
      mid
    )

  def make: Either[UnsupportedProtocolError, service.Impl[ResourcefulTask]] = {

    checkProtocol(service, protocolTag)
      // Making sure the router is evaluated lazily, so that all the compilation inside it
      // doesn't happen in case of a missing protocol
      .map { _ =>
        service.impl {
          implicit val monadThrow = zioMonadThrowLike[Scope]
          UnaryClientCompiler(
            service,
            client,
            (client: Client) => ZHttpToSmithy4sClient(client),
            simpleProtocolCodecs.makeClientCodecs(url),
            middleware,
            (response: Response) => response.status.isSuccess
          )
        }
      }
  }

  def lift: IO[UnsupportedProtocolError, service.Impl[ResourcefulTask]] =
    ZIO.fromEither(make)
}
