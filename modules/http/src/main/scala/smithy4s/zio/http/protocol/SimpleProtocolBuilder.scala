package smithy4s.zio.http.protocol

import smithy4s.kinds.FunctorAlgebra
import smithy4s.zio.http.internal.builders.client.ClientBuilder
import smithy4s.zio.http.internal.builders.server.RouterBuilder
import smithy4s.{Endpoint, ShapeTag}
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
