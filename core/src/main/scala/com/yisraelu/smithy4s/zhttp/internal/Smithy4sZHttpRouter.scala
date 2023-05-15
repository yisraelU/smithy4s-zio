package com.yisraelu.smithy4s.zhttp.internal

import com.yisraelu.smithy4s.zhttp.EntityCompiler
import com.yisraelu.smithy4s.zhttp.collectFirstSome
import smithy4s.http.PathParams
import smithy4s.kinds.BiFunctorInterpreter
import zio.http._
import zio.IO

class Smithy4sZHttpRouter[Alg[_[_, _, _, _, _]], Op[_, _, _, _, _]](
    service: smithy4s.Service.Aux[Alg, Op],
    impl: BiFunctorInterpreter[Op, IO],
    entityCompiler: EntityCompiler
) {
  private val compilerContext = CompilerContext.make(entityCompiler)
  def routes: EHttpApp = Http.fromHandlerZIO {
    Function.unlift({ (request: Request) =>
      {
        for {
          endpoints <- perMethodEndpoint.get(request.method)
          path = request.url.path.segments.toArray.map(_.toString)
          (endpoint, pathParams) <- collectFirstSome[
            Smithy4sZHttpServerEndpoint[_],
            (Smithy4sZHttpServerEndpoint[_], PathParams)
          ](endpoints)(_.matchPath(path))
        } yield endpoint.run(pathParams, request)
      }
    })

  }

  private val zHttpEndpoints: List[Smithy4sZHttpServerEndpoint[_]] =
    service.endpoints
      .map { ep =>
        Smithy4sZHttpServerEndpoint(
          impl,
          ep,
          compilerContext
        )
      }
      .collect { case Right(zHttpServerEndpoint) =>
        zHttpServerEndpoint
      }

  private val perMethodEndpoint
      : Map[Method, List[Smithy4sZHttpServerEndpoint[_]]] =
    zHttpEndpoints.groupBy(_.method)

}
