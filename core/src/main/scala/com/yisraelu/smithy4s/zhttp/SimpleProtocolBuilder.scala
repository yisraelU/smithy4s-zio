package com.yisraelu.smithy4s.zhttp

import _root_.zio.{IO, ZIO}
import com.yisraelu.smithy4s.zhttp.internal.{
  Smithy4sZHttpReverseRouter,
  Smithy4sZHttpRouter
}
import smithy4s.{checkProtocol, ShapeTag, UnsupportedProtocolError}
import smithy4s.http.CodecAPI
import smithy4s.kinds.{BiFunctorAlgebra, Kind2}
import zio.http._

/**
 * Abstract construct helping the construction of routers and clients
 * for a given protocol. Upon constructing the routers/clients, it will
 * first check that they are indeed annotated with the protocol in question.
 */
abstract class SimpleProtocolBuilder[P](val codecs: CodecAPI)(implicit
    protocolTag: ShapeTag[P]
) {

  def apply[Alg[_[_, _, _, _, _]]](
      service: smithy4s.Service[Alg]
  ): ServiceBuilder[Alg] = new ServiceBuilder(service)

  class ServiceBuilder[Alg[_[_, _, _, _, _]]](
      service: smithy4s.Service[Alg]
  ) {

    def routes(
        impl: BiFunctorAlgebra[Alg, IO]
    )(implicit
        service: smithy4s.Service[Alg]
    ): RouterBuilder[Alg] = {
      new RouterBuilder[Alg](
        service,
        impl
      )
    }

    def client(
        baseUrl: URL,
        zClient: Client
    ): IO[UnsupportedProtocolError, BiFunctorAlgebra[Alg, IO]] =
      ZIO.fromEither(
        checkProtocol(service, protocolTag)
          .map(_ =>
            new Smithy4sZHttpReverseRouter[Alg, service.Operation](
              baseUrl,
              service,
              zClient,
              EntityCompiler
                .fromCodecAPI(codecs)
            )
          )
          .map(service.fromPolyFunction[Kind2[IO]#toKind5](_))
      )

  }

  class RouterBuilder[
      Alg[_[_, _, _, _, _]]
  ](
      service: smithy4s.Service[Alg],
      impl: BiFunctorAlgebra[Alg, IO]
  ) {

    val entityCompiler = EntityCompiler.fromCodecAPI(codecs)

    def make: IO[UnsupportedProtocolError, EHttpApp] =
      ZIO.fromEither(checkProtocol(service, protocolTag)).as {
        new Smithy4sZHttpRouter[Alg, service.Operation](
          service,
          service.toPolyFunction[Kind2[IO]#toKind5](impl),
          entityCompiler
        ).routes
      }

  }
}
