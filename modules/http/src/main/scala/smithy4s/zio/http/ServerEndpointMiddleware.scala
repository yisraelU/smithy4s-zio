package smithy4s.zio.http

import smithy4s.zio.http.internal.EffectOps
import smithy4s.{Endpoint, Service}
import zio.http.{EHttpApp, Http, Request, Response}
import zio.{Task, ZIO}

object ServerEndpointMiddleware {

  trait Simple extends Endpoint.Middleware.Simple[EHttpApp]

  def mapErrors(
      f: PartialFunction[Throwable, Throwable]
  ): ServerEndpointMiddleware = flatMapErrors(f.andThen(_.pure))

  def flatMapErrors(
      f: PartialFunction[Throwable, Task[Throwable]]
  ): ServerEndpointMiddleware =
    new ServerEndpointMiddleware {
      def prepare[Alg[_[_, _, _, _, _]]](service: Service[Alg])(
          endpoint: Endpoint[service.Operation, _, _, _, _, _]
      ): EHttpApp => EHttpApp = http => {
        val handler: PartialFunction[Throwable, Task[Response]] = {
          case e @ endpoint.Error(_, _) => ZIO.fail(e)
          case scala.util.control.NonFatal(other) if f.isDefinedAt(other) =>
            f(other).flatMap(ZIO.fail(_))
        }
        Http.collectZIO[Request] { req =>
          http
            .runZIO(req)
            .mapError {
              case None    => new Exception("No response")
              case Some(e) => e
            }
            .catchSome(handler)

        }
      }
    }
}
