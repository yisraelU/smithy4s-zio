package smithy4s.zio.http

import smithy4s.Interpreter
import smithy4s.http.PathParams
import smithy4s.zio.http.internal.Smithy4sZHttpServerEndpoint
import zhttp.http.{Http, HttpApp, Method, Request}
import zio.Task

// format: off
class Smithy4sZHttpRouter[Alg[_[_, _, _, _, _]], Op[_, _, _, _, _]](
                                                                          service: smithy4s.Service[Alg, Op],
                                                                          impl: Interpreter[Op, Task],
                                                                          errorTransformation: PartialFunction[Throwable, Throwable],
                                                                          codecs: EntityCompiler
                                                                        ){

  val routes =  Http.fromFunctionZIO { {(request:Request) => {
    for {
      endpoints <- perMethodEndpoint.get(request.method)
      path = request.url.path.segments.toArray.map(_.toString)
      (endpoint, pathParams) <- collectFirstSome[Smithy4sZHttpServerEndpoint, (Smithy4sZHttpServerEndpoint, PathParams)](endpoints)(_.matchTap(path))
    } yield endpoint.run(pathParams, request)
  }}.unlift
  }
  // format: on

  private val zHttpEndpoints: List[Smithy4sZHttpServerEndpoint] =
    service.endpoints
      .map { ep =>
        Smithy4sZHttpServerEndpoint(
          impl,
          ep,
          codecs,
          errorTransformation
        )
      }
      .collect { case Some(zHttpServerEndpoint) =>
        zHttpServerEndpoint
      }

  private val perMethodEndpoint
  : Map[Method, List[Smithy4sZHttpServerEndpoint]] =
    zHttpEndpoints.groupBy(_.method)

}
