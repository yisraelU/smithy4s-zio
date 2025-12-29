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
    middleware: ServerEndpointMiddleware = Endpoint.Middleware.noop[HttpRoutes],
    onError: PartialFunction[Throwable, Task[Unit]] = PartialFunction.empty,
    encodeErrorsBeforeMiddleware: Boolean = false
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
      middleware,
      onError,
      encodeErrorsBeforeMiddleware
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
      middleware,
      onError,
      encodeErrorsBeforeMiddleware
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
      mid,
      onError,
      encodeErrorsBeforeMiddleware
    )

  def onError(
      callback: PartialFunction[Throwable, Task[Unit]]
  ): RouterBuilder[Alg, P] =
    new RouterBuilder[Alg, P](
      protocolTag,
      simpleProtocolCodecs,
      service,
      impl,
      fe,
      middleware,
      callback,
      encodeErrorsBeforeMiddleware
    )

  def make: Either[UnsupportedProtocolError, HttpRoutes] =
    checkProtocol(service, protocolTag)
      .left.map { error =>
        enrichProtocolError(error, service.id.name, protocolTag.id, isClient = false)
      }
      // Making sure the router is evaluated lazily, so that all the compilation inside it
      // doesn't happen in case of a missing protocol
      .map { _ =>
        implicit val monadThrow = zioMonadThrowLike[Any]
        val errorHandler: ServerEndpointMiddleware =
          ServerEndpointMiddleware.flatMapErrors(fe)
        // Apply error handler before and after user middleware
        // First: transforms service errors (allows non-contract → contract error conversion)
        // Second: handles errors thrown by middleware itself
        val finalMiddleware: Endpoint.Middleware[HttpRoutes] =
          errorHandler.andThen(middleware).andThen(errorHandler)

        val router: Request => Option[Task[Response]] =
          HttpUnaryServerRouter(service, encodeErrorsBeforeMiddleware, onError)(
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

  def lift: IO[UnsupportedProtocolError, HttpRoutes] = ZIO.fromEither(make)

  /**
   * Builds the routes and sandboxes them, converting errors to HTTP responses.
   * This is a convenience method equivalent to `make.map(_.sandbox)`.
   *
   * The sandboxed routes are ready to be passed to `Server.serve` without
   * additional error handling.
   *
   * @return Either a protocol error or sandboxed routes ready for serving
   */
  def makeApp: Either[UnsupportedProtocolError, Routes[Any, Response]] =
    make.map(_.sandbox)

  /**
   * Effectful version of `makeApp` that returns a ZIO effect.
   * Builds the routes and sandboxes them, converting errors to HTTP responses.
   *
   * @return ZIO effect that produces sandboxed routes ready for serving
   */
  def liftApp: IO[UnsupportedProtocolError, Routes[Any, Response]] =
    ZIO.fromEither(makeApp)

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
            b(requestAndPath._2)
          )
        val singleRoute = Route.route(RoutePattern.any)(handler)
        Routes(singleRoute)
      }
    }
}
