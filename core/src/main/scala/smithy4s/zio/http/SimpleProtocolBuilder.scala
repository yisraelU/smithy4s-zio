package smithy4s.zio.http

import smithy4s.{checkProtocol, GenLift, Interpreter, Monadic, ShapeTag, UnsupportedProtocolError}
import smithy4s.http.CodecAPI
import zhttp.http.{Http, HttpApp, Request, Response, RHttpApp, URL}
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

    def routes[Alg[_[_, _, _, _, _]], Op[_, _, _, _, _]](
                                                                impl: Monadic[Alg, Task]
                                                              )(implicit
                                                                serviceProvider: smithy4s.Service.Provider[Alg, Op]
                                                              ): RouterBuilder[Alg, Op] = {
      val service = serviceProvider.service
      new RouterBuilder[Alg, Op](
        service,
        service.asTransformation[GenLift[Task]#λ](impl),
        PartialFunction.empty
      )
    }

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

  class RouterBuilder[
    Alg[_[_, _, _, _, _]],
    Op[_, _, _, _, _]
  ] (
                                          service: smithy4s.Service[Alg, Op],
                                          impl: Interpreter[Op, Task],
                                          errorTransformation: PartialFunction[Throwable, Task[Throwable]]
                                        ){

    val entityCompiler =
      EntityCompiler.fromCodecAPI(codecs)

    def mapErrors(
                   fe: PartialFunction[Throwable, Throwable]
                 ): RouterBuilder[Alg, Op] =
      new RouterBuilder(service, impl, fe andThen(e => ZIO.fail(e)))

    def flatMapErrors(
                       fe: PartialFunction[Throwable, Task[Throwable]]
                     ): RouterBuilder[Alg, Op] =
      new RouterBuilder(service, impl, fe)

    def make[R]: ZIO[Any, UnsupportedProtocolError, RHttpApp[R]] =
      ZIO.fromEither(checkProtocol(service, protocolTag)).as {
        new Smithy4sZHttpRouter[Alg, Op ](
          service,
          impl,
          errorTransformation,
          entityCompiler
        ).routes
      }

  }
}
