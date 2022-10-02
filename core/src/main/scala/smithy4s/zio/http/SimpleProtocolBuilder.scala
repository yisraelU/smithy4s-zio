package smithy4s.zio.http

import smithy4s.{checkProtocol, GenLift, Monadic, ShapeTag, UnsupportedProtocolError}
import smithy4s.http.CodecAPI
import zhttp.http.URL
import zhttp.service.Client
import zio.{IO, Task, ZIO}

/**
 * Abstract construct helping the construction of routers and clients
 * for a given protocol. Upon constructing the routers/clients, it will
 * first check that they are indeed annotated with the protocol in question.
 */
abstract class SimpleProtocolBuilder[P](val codecs: CodecAPI)(implicit
    protocolTag: ShapeTag[P]
) {

  def apply[Alg[_[_, _, _, _, _]], Op[_, _, _, _, _]](
      serviceProvider: smithy4s.Service.Provider[Alg, Op]
  ): ServiceBuilder[Alg, Op] = new ServiceBuilder(serviceProvider.service)

  class ServiceBuilder[Alg[_[_, _, _, _, _]], Op[_, _, _, _, _]](
      service: smithy4s.Service[Alg, Op]
  ) {

    def client[R](
        baseUrl: URL,
        zClient: Client[R]
    ): IO[UnsupportedProtocolError, Monadic[Alg, Task]] =
      ZIO.fromEither(checkProtocol(service, protocolTag)
        .map(_ =>
          new Smithy4sZHttpReverseRouter[R,Alg, Op](
            baseUrl,
            service,
            zClient,
            EntityCompiler
              .fromCodecAPI(codecs)
          )
        )
        .map(service.transform[GenLift[Task]#λ]))

  }
}
