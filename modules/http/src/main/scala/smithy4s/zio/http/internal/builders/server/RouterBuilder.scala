package smithy4s.zio.http.internal.builders.server

import smithy4s.http.HttpUnaryServerRouter
import smithy4s.kinds.FunctorAlgebra
import smithy4s.zio.http.internal.{
  tagRequest,
  toSmithy4sHttpMethod,
  toSmithy4sHttpUri,
  zioMonadThrowLike
}
import smithy4s.zio.http.middleware.ServerEndpointMiddleware
import smithy4s.zio.http.protocol.SimpleProtocolCodecs
import smithy4s.zio.http.{HttpRoutes, ServerEndpointMiddleware, SimpleHandler}
import smithy4s.{
  Bijection,
  Endpoint,
  ShapeTag,
  UnsupportedProtocolError,
  checkProtocol
}
import zio.http.*
import zio.{IO, Task, ZIO}

class RouterBuilder[
    Alg[_[_, _, _, _, _]],
    P
] private[http] (
    protocolTag: ShapeTag[P],
    simpleProtocolCodecs: SimpleProtocolCodecs,
    service: smithy4s.Service[Alg],
    impl: FunctorAlgebra[Alg, Task],
    fe: PartialFunction[Throwable, Task[Throwable]],
    middleware: ServerEndpointMiddleware = Endpoint.Middleware.noop[HttpRoutes]
) {

  def mapErrors(
      fe: PartialFunction[Throwable, Throwable]
  ): RouterBuilder[Alg, P] =
    new RouterBuilder(
      protocolTag,
      simpleProtocolCodecs,
      service,
      impl,
      fe andThen (e => ZIO.succeed(e)),
      middleware
    )

  def flatMapErrors(
      fe: PartialFunction[Throwable, Task[Throwable]]
  ): RouterBuilder[Alg, P] =
    new RouterBuilder(
      protocolTag,
      simpleProtocolCodecs,
      service,
      impl,
      fe,
      middleware
    )

  def middleware(
      mid: ServerEndpointMiddleware
  ): RouterBuilder[Alg, P] =
    new RouterBuilder[Alg, P](
      protocolTag,
      simpleProtocolCodecs,
      service,
      impl,
      fe,
      mid
    )

  def make: Either[UnsupportedProtocolError, HttpRoutes] =
    checkProtocol(service, protocolTag)
      // Making sure the router is evaluated lazily, so that all the compilation inside it
      // doesn't happen in case of a missing protocol
      .map { _ =>
        implicit val monadThrow = zioMonadThrowLike[Any]
        val errorHandler: ServerEndpointMiddleware =
          ServerEndpointMiddleware.flatMapErrors(fe)
        val finalMiddleware: Endpoint.Middleware[HttpRoutes] =
          errorHandler.andThen(middleware).andThen(errorHandler)

        val router: Request => Option[Task[Response]] =
          HttpUnaryServerRouter(service)(
            impl,
            simpleProtocolCodecs.makeServerCodecs,
            finalMiddleware.biject[SimpleHandler](bijection.to)(bijection.from),
            getMethod =
              (request: Request) => toSmithy4sHttpMethod(request.method),
            getUri = (request: Request) => toSmithy4sHttpUri(request.url, None),
            addDecodedPathParams =
              (request: Request, pathParams) => tagRequest(request, pathParams)
          )

        val ir: Request => Task[Response] =
          router(_).getOrElse(ZIO.succeed(Response.status(Status.NotFound)))
        bijection.from(ir)
      }

  def lift: IO[UnsupportedProtocolError, HttpRoutes] = ZIO.fromEither(make)

  val bijection: Bijection[HttpRoutes, SimpleHandler] =
    new Bijection[HttpRoutes, SimpleHandler] {
      override def to(httpRoutes: HttpRoutes): SimpleHandler = {
        val absolved = httpRoutes.handleError(throw _)
        (
            (req: Request) => absolved.runZIO(req)
        ).asInstanceOf[SimpleHandler]
      }

      override def from(b: SimpleHandler): HttpRoutes = {
        val handler: Handler[Any, Throwable, (Path, Request), Response] =
          Handler.fromFunctionZIO[(Path, Request)](requestAndPath =>
            b(requestAndPath._2).catchAllDefect(e => ZIO.die(e))
          )
        val singleRoute = Route.route(RoutePattern.any)(handler)
        Routes(singleRoute)
      }
    }
}
