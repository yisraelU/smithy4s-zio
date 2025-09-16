package smithy4s.zio.http.middleware

import smithy4s.zio.http.internal.EffectOps
import smithy4s.zio.http.{HttpRoutes, ServerEndpointMiddleware}
import smithy4s.{Endpoint, Service}
import zio.{Task, ZIO}

object ServerEndpointMiddleware {

  trait Simple[F[_]] extends Endpoint.Middleware.Simple[HttpRoutes]

  def mapErrors(
      f: PartialFunction[Throwable, Throwable]
  ): ServerEndpointMiddleware = flatMapErrors(f(_).pure)

  def flatMapErrors(
      f: PartialFunction[Throwable, Task[Throwable]]
  ): ServerEndpointMiddleware =
    new ServerEndpointMiddleware {
      def prepare[Alg[_[_, _, _, _, _]]](service: Service[Alg])(
          endpoint: Endpoint[service.Operation, ?, ?, ?, ?, ?]
      ): HttpRoutes => HttpRoutes = routes => {
        /* val fx: PartialFunction[Throwable, Task[Nothing]] = {
          case e @ endpoint.Error(_, _) => ZIO.die(e)
          case scala.util.control.NonFatal(other) if f.isDefinedAt(other) =>
            f(other).flatMap(ZIO.die(_))
        }*/

        f.andThen(_.flatMap(ZIO.die(_)))
        // todo pending error mapping added to Routes
        routes
      }
    }

}
