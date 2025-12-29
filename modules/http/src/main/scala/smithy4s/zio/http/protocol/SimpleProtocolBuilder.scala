package smithy4s.zio.http.protocol

import smithy4s.kinds.FunctorAlgebra
import smithy4s.zio.http.internal.builders.client.ClientBuilder
import smithy4s.zio.http.internal.builders.server.RouterBuilder
import smithy4s.zio.http.HttpRoutes
import smithy4s.{Endpoint, ShapeTag, UnsupportedProtocolError}
import zio.Task
import zio.http.*

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
  ): RouterBuilder[Alg, P] = {
    new RouterBuilder[Alg, P](
      protocolTag,
      simpleProtocolCodecs,
      service,
      impl,
      PartialFunction.empty,
      Endpoint.Middleware.noop
    )
  }

  /**
   * Creates and builds routes with configuration function (http4s-style DSL).
   *
   * This allows you to configure the router inline:
   * {{{
   *   import smithy4s.zio.http.SimpleRestJsonBuilder._
   *
   *   routesWith(myServiceImpl) { builder =>
   *     builder
   *       .middleware(loggingMiddleware)
   *       .mapErrors { case e: MyError => MyMappedError(e) }
   *       .onError { case e => logError(e) }
   *   }
   * }}}
   *
   * @param impl the service implementation
   * @param configure function to configure the RouterBuilder
   * @return Either protocol error or configured HttpRoutes
   */
  def routesWith[Alg[_[_, _, _, _, _]]](
      impl: FunctorAlgebra[Alg, Task]
  )(
      configure: RouterBuilder[Alg, P] => RouterBuilder[Alg, P]
  )(implicit
      service: smithy4s.Service[Alg]
  ): Either[UnsupportedProtocolError, HttpRoutes] = {
    val builder = new RouterBuilder[Alg, P](
      protocolTag,
      simpleProtocolCodecs,
      service,
      impl,
      PartialFunction.empty,
      Endpoint.Middleware.noop
    )
    configure(builder).make
  }

  /**
   * Creates and builds sandboxed routes with configuration function.
   *
   * Returns Routes ready to pass to Server.serve.
   *
   * {{{
   *   import smithy4s.zio.http.SimpleRestJsonBuilder._
   *
   *   routesAppWith(myServiceImpl) { builder =>
   *     builder.middleware(loggingMiddleware)
   *   }
   * }}}
   *
   * @param impl the service implementation
   * @param configure function to configure the RouterBuilder
   * @return Either protocol error or sandboxed Routes ready for serving
   */
  def routesAppWith[Alg[_[_, _, _, _, _]]](
      impl: FunctorAlgebra[Alg, Task]
  )(
      configure: RouterBuilder[Alg, P] => RouterBuilder[Alg, P]
  )(implicit
      service: smithy4s.Service[Alg]
  ): Either[UnsupportedProtocolError, Routes[Any, Response]] = {
    val builder = new RouterBuilder[Alg, P](
      protocolTag,
      simpleProtocolCodecs,
      service,
      impl,
      PartialFunction.empty,
      Endpoint.Middleware.noop
    )
    configure(builder).makeApp
  }

  class ServiceBuilder[
      Alg[_[_, _, _, _, _]]
  ] private[http] (val service: smithy4s.Service[Alg]) {
    self =>

    def routes(
        impl: FunctorAlgebra[Alg, Task]
    )(implicit
        service: smithy4s.Service[Alg]
    ): RouterBuilder[Alg, P] = {
      new RouterBuilder[Alg, P](
        protocolTag,
        simpleProtocolCodecs,
        service,
        impl,
        PartialFunction.empty,
        Endpoint.Middleware.noop
      )
    }

    def client(client: Client) =
      new ClientBuilder[Alg, P](simpleProtocolCodecs, client, service)

  }
}
