package smithy4s.zio.http

import smithy4s.client.UnaryClientCompiler
import smithy4s.http.HttpUnaryServerRouter
import smithy4s.kinds._
import smithy4s.zio.http.internal.{
  ZHttpToSmithy4sClient,
  toSmithy4sHttpMethod,
  toSmithy4sHttpUri,
  zioMonadThrowLike
}
import smithy4s.{Endpoint, ShapeTag, UnsupportedProtocolError, checkProtocol}
import zio.IsSubtypeOfError.impl
import zio.http._
import zio.{IO, Task, ZIO}

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

  def routes[Alg[_[_, _, _, _, _]]](
      impl: FunctorAlgebra[Alg, Task]
  )(implicit
      service: smithy4s.Service[Alg]
  ): RouterBuilder[Alg] = {
    new RouterBuilder[Alg](
      service,
      impl,
      PartialFunction.empty,
      Endpoint.Middleware.noop
    )
  }

  class ServiceBuilder[
      Alg[_[_, _, _, _, _]]
  ] private[http] (val service: smithy4s.Service[Alg]) {
    self =>

    def client(client: Client) =
      new ClientBuilder[Alg](client, service)

    def routes(
        impl: FunctorAlgebra[Alg, Task]
    ): RouterBuilder[Alg] =
      new RouterBuilder[Alg](
        service,
        impl,
        PartialFunction.empty,
        Endpoint.Middleware.noop
      )

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

    def make: Either[UnsupportedProtocolError, service.Impl[Task]] = {
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

    def lift: IO[UnsupportedProtocolError, service.Impl[Task]] =
      ZIO.fromEither(make)
  }

  class RouterBuilder[
      Alg[_[_, _, _, _, _]]
  ] private[http] (
      service: smithy4s.Service[Alg],
      impl: FunctorAlgebra[Alg, Task],
      errorTransformation: PartialFunction[Throwable, Task[Throwable]],
      middleware: ServerEndpointMiddleware
  ) {

    /**
     * Applies the error transformation to the errors that are not in the smithy spec (has no effect on errors from spec).
     * Transformed errors raised in endpoint implementation will be observable from [[middleware]].
     * Errors raised in the [[middleware]] will be transformed too.
     *
     * The following two are equivalent:
     * {{{
     * val handlerPF: PartialFunction[Throwable, Throwable] = ???
     * builder.mapErrors(handlerPF).middleware(middleware)
     * }}}
     *
     * {{{
     * val handlerPF: PartialFunction[Throwable, Throwable] = ???
     * val handler = ServerEndpointMiddleware.mapErrors(handlerPF)
     * builder.middleware(handler |+| middleware |+| handler)
     * }}}
     */
    def mapErrors(
        fe: PartialFunction[Throwable, Throwable]
    ): RouterBuilder[Alg] =
      new RouterBuilder(
        service,
        impl,
        fe andThen (e => ZIO.succeed(e)),
        middleware
      )

    /**
     * Applies the error transformation to the errors that are not in the smithy spec (has no effect on errors from spec).
     * Transformed errors raised in endpoint implementation will be observable from [[middleware]].
     * Errors raised in the [[middleware]] will be transformed too.
     *
     * The following two are equivalent:
     * {{{
     * val handlerPF: PartialFunction[Throwable, F[Throwable]] = ???
     * builder.flatMapErrors(handlerPF).middleware(middleware)
     * }}}
     *
     * {{{
     * val handlerPF: PartialFunction[Throwable, F[Throwable]] = ???
     * val handler = ServerEndpointMiddleware.flatMapErrors(handlerPF)
     * builder.middleware(handler |+| middleware |+| handler)
     * }}}
     */
    def flatMapErrors(
        fe: PartialFunction[Throwable, Task[Throwable]]
    ): RouterBuilder[Alg] =
      new RouterBuilder(service, impl, fe, middleware)

    def middleware(
        mid: ServerEndpointMiddleware
    ): RouterBuilder[Alg] =
      new RouterBuilder[Alg](service, impl, errorTransformation, mid)

    def make: Either[UnsupportedProtocolError, EHttpApp] =
      checkProtocol(service, protocolTag)
        // Making sure the router is evaluated lazily, so that all the compilation inside it
        // doesn't happen in case of a missing protocol
        .map { _ =>
          val errorHandler =
            ServerEndpointMiddleware.flatMapErrors(errorTransformation)
          val finalMiddleware =
            errorHandler.andThen(middleware).andThen(errorHandler)
          val router =
            HttpUnaryServerRouter(service)(
              impl,
              simpleProtocolCodecs.makeServerCodecs,
              finalMiddleware.biject(app =>
                app
                  .runZIO(_: Request)
                  .flattenErrorOption[Throwable, Throwable](
                    new Exception("No response")
                  )
              )(fxn => Http.collectZIO(PartialFunction.fromFunction(fxn))),
              getMethod =
                (request: Request) => toSmithy4sHttpMethod(request.method),
              getUri =
                (request: Request) => toSmithy4sHttpUri(request.url, None),
              addDecodedPathParams = (request: Request, pathParams) =>
                // todo: add path params
                request
            )
          Http.collectZIO { req =>
            router(req).getOrElse(ZIO.fail(new Exception("No response")))
          }
        }

    def lift: IO[UnsupportedProtocolError, EHttpApp] = ZIO.fromEither(make)
  }
}
