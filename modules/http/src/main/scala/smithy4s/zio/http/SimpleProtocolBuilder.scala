package smithy4s.zio.http

import smithy4s.client.UnaryClientCompiler
import smithy4s.zio.http.internal.ZHttpToSmithy4sClient.ResourcefulTask
import smithy4s.zio.http.internal.{ZHttpToSmithy4sClient, zioMonadThrowLike}
import smithy4s.{Endpoint, ShapeTag, UnsupportedProtocolError, checkProtocol}
import zio.http.*
import zio.{IO, ZIO}

/**
 * Abstract construct helping the construction of routers and clients
 * for a given protocol. Upon constructing the routers/clients, it will
 * first check that they are indeed annotated with the protocol in question.
 */
abstract class SimpleProtocolBuilder[P](
    simpleProtocolCodecs: SimpleProtocolCodecs
)(implicit
    protocolTag: ShapeTag[P]
) {

  def apply[Alg[_[_, _, _, _, _]]](
      service: smithy4s.Service[Alg]
  ): ServiceBuilder[Alg] = new ServiceBuilder(service)

  class ServiceBuilder[
      Alg[_[_, _, _, _, _]]
  ] private[http] (val service: smithy4s.Service[Alg]) {
    self =>

    def client(client: Client) =
      new ClientBuilder[Alg](client, service)
  }

  class ClientBuilder[
      Alg[_[_, _, _, _, _]]
  ] private[http] (
      client: Client,
      val service: smithy4s.Service[Alg],
      url: URL = URL
        .decode("http://localhost:8080")
        .getOrElse(throw new Exception("Invalid URL")),
      middleware: ClientEndpointMiddleware = Endpoint.Middleware.noop[Client]
  ) {

    def uri(uri: URL): ClientBuilder[Alg] =
      new ClientBuilder[Alg](this.client, this.service, uri, this.middleware)

    def middleware(
        mid: ClientEndpointMiddleware
    ): ClientBuilder[Alg] =
      new ClientBuilder[Alg](this.client, this.service, this.url, mid)

    def make
        : Either[UnsupportedProtocolError, service.Impl[ResourcefulTask]] = {
      checkProtocol(service, protocolTag)
        // Making sure the router is evaluated lazily, so that all the compilation inside it
        // doesn't happen in case of a missing protocol
        .map { _ =>
          service.impl {
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

}
