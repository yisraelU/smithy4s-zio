package com.yisraelu.smithy4s.zhttp.internal

import com.yisraelu.smithy4s.zhttp.EntityCompiler
import com.yisraelu.smithy4s.zhttp.EntityCompiler
import smithy4s.Endpoint
import smithy4s.kinds.{BiFunctorInterpreter, PolyFunction5}
import zio.IO
import zio.http._

private[zhttp] object Smithy4sZHttpReverseRouter {

  def impl[Alg[_[_, _, _, _, _]]](
      baseUri: URL,
      service: smithy4s.Service[Alg],
      client: Client,
      entityCompiler: EntityCompiler
  ) = service.bifunctorInterpreter {
    new service.BiFunctorEndpointCompiler[IO] {
      private val compilerContext = CompilerContext.make(entityCompiler)
      def apply[I, E, O, SI, SO](
          endpoint: service.Endpoint[I, E, O, SI, SO]
      ): I => IO[E, O] =
        Smithy4sZHttpClientEndpoint(
          baseUri,
          client,
          endpoint,
          compilerContext
        )
    }
  }

}
