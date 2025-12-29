package smithy4s.zio.http.internal.builders.client

import smithy4s.client.UnaryClientCompiler
import smithy4s.zio.http.internal.{ZHttpToSmithy4sClient, zioMonadThrowLike}
import smithy4s.zio.http.protocol.SimpleProtocolCodecs
import smithy4s.zio.http.{ClientEndpointMiddleware, ResourcefulTask}
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

  def url(url: URL): ClientBuilder[Alg, P] =
    new ClientBuilder[Alg, P](
      simpleProtocolCodecs,
      this.client,
      this.service,
      url,
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
      .left.map { error =>
        enrichProtocolError(error, service.id.name, protocolTag.id, isClient = true)
      }
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
            (response: Response) => !response.status.isError
          )
        }
      }
  }

  private def enrichProtocolError(
      original: UnsupportedProtocolError,
      serviceName: String,
      expectedProtocol: smithy4s.ShapeId,
      isClient: Boolean
  ): UnsupportedProtocolError = {
    val componentType = if (isClient) "client" else "server"
    val enrichedMessage =
      s"""
         |━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
         |❌ Protocol Mismatch: Cannot build $componentType for service '$serviceName'
         |━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
         |
         |Expected protocol: ${expectedProtocol.show}
         |
         |${original.getMessage}
         |
         |💡 How to fix:
         |  1. Add the protocol annotation to your Smithy service:
         |
         |     use ${expectedProtocol.namespace}#${expectedProtocol.name}
         |
         |     @${expectedProtocol.name}
         |     service $serviceName {
         |       version: "1.0.0"
         |       operations: [YourOperations]
         |     }
         |
         |  2. Or use a different builder that matches your service's actual protocol
         |
         |📚 Docs: https://yisraelu.github.io/smithy4s-zio/
         |━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
         |""".stripMargin

    // Create a new exception with enriched message in the cause
    val enriched = new UnsupportedProtocolError(service, protocolTag)
    enriched.initCause(new Exception(enrichedMessage, original))
    enriched
  }

  def lift: IO[UnsupportedProtocolError, service.Impl[ResourcefulTask]] =
    ZIO.fromEither(make)
}
