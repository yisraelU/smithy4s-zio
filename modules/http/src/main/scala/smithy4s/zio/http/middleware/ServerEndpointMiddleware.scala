package smithy4s.zio.http.middleware

import smithy4s.zio.http.internal.EffectOps
import smithy4s.zio.http.{HttpRoutes, ServerEndpointMiddleware}
import smithy4s.{Endpoint, Service}
import zio.http.{Handler, Route, RoutePattern, Routes}
import zio.{Task, ZIO}

object ServerEndpointMiddleware {

  trait Simple[F[_]] extends Endpoint.Middleware.Simple[HttpRoutes]

  /**
   * Creates middleware that maps errors using a synchronous function.
   *
   * Note: Smithy4s endpoint errors (typed errors from the service definition)
   * are preserved and not transformed, as they are part of the service contract.
   *
   * @param f partial function to transform non-endpoint errors
   * @return middleware that applies the error transformation
   */
  def mapErrors(
      f: PartialFunction[Throwable, Throwable]
  ): ServerEndpointMiddleware = flatMapErrors(f(_).pure)

  /**
   * Creates middleware that maps errors using an effectful function.
   *
   * This allows error transformation logic that may itself perform effects.
   * Smithy4s endpoint errors are preserved as they represent the service's
   * error contract.
   *
   * @param f partial function to transform non-endpoint errors (may be effectful)
   * @return middleware that applies the error transformation
   */
  def flatMapErrors(
      f: PartialFunction[Throwable, Task[Throwable]]
  ): ServerEndpointMiddleware =
    new ServerEndpointMiddleware {
      def prepare[Alg[_[_, _, _, _, _]]](service: Service[Alg])(
          endpoint: Endpoint[service.Operation, ?, ?, ?, ?, ?]
      ): HttpRoutes => HttpRoutes = routes => {
        // Create a handler that wraps the routes and transforms errors
        // The pattern: absorb errors -> run -> catch defects -> transform -> re-fail
        val handler: Handler[
          Any,
          Throwable,
          (zio.http.Path, zio.http.Request),
          zio.http.Response
        ] =
          Handler.fromFunctionZIO {
            (pathAndRequest: (zio.http.Path, zio.http.Request)) =>
              val (_, request) = pathAndRequest

              // Absorb errors into defects so we can call runZIO
              val absolved = routes.handleError(throw _)

              // Run the routes to get the response effect
              val responseEffect = absolved.runZIO(request)

              // Catch defects (which include the absorbed errors) and transform them
              responseEffect.catchAllDefect { defect =>
                defect match {
                  case e @ endpoint.Error(_, _) =>
                    // Preserve smithy4s endpoint errors in the error channel
                    // These are part of the service contract and should be encoded by the protocol
                    ZIO.fail(e)
                  case scala.util.control.NonFatal(other)
                      if f.isDefinedAt(other) =>
                    // Transform other non-fatal errors and put them in the error channel
                    f(other).flatMap(ZIO.fail(_))
                  case other =>
                    // Pass through errors not matching the partial function
                    ZIO.fail(other)
                }
              }
          }

        // Wrap the handler in a route that matches any path
        val singleRoute = Route.route(RoutePattern.any)(handler)
        Routes(singleRoute)
      }
    }

  /**
   * Creates middleware that observes errors without transforming them.
   *
   * This allows running side effects (like logging) when errors occur,
   * while preserving the original error for protocol encoding.
   *
   * @param f partial function to run effects on errors
   * @return middleware that observes errors
   */
  def tapErrors(
      f: PartialFunction[Throwable, Task[Unit]]
  ): ServerEndpointMiddleware =
    new ServerEndpointMiddleware {
      def prepare[Alg[_[_, _, _, _, _]]](service: Service[Alg])(
          endpoint: Endpoint[service.Operation, ?, ?, ?, ?, ?]
      ): HttpRoutes => HttpRoutes = routes => {
        // Create a handler that wraps the routes and observes errors
        val handler: Handler[
          Any,
          Throwable,
          (zio.http.Path, zio.http.Request),
          zio.http.Response
        ] =
          Handler.fromFunctionZIO {
            (pathAndRequest: (zio.http.Path, zio.http.Request)) =>
              val (_, request) = pathAndRequest

              // Absorb errors into defects so we can call runZIO
              val absolved = routes.handleError(throw _)

              // Run the routes to get the response effect
              val responseEffect = absolved.runZIO(request)

              // Catch defects and run callback, then re-fail with original error
              responseEffect.catchAllDefect { defect =>
                defect match {
                  case scala.util.control.NonFatal(e) if f.isDefinedAt(e) =>
                    // Run the callback effect, then re-fail with original error
                    f(e).as(ZIO.fail(e)).flatten
                  case other =>
                    // Pass through errors not matching the partial function
                    ZIO.fail(other)
                }
              }
          }

        // Wrap the handler in a route that matches any path
        val singleRoute = Route.route(RoutePattern.any)(handler)
        Routes(singleRoute)
      }
    }

}
