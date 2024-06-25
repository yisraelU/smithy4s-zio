package smithy4s.zio.http.middleware

import smithy4s.zio.http.ClientEndpointMiddleware
import smithy4s.{Endpoint, Hints, Service}
import zio.http.Client

object ClientEndpointMiddleware {

  trait Simple extends ClientEndpointMiddleware {
    def prepareWithHints(
        serviceHints: Hints,
        endpointHints: Hints
    ): Client => Client

    final def prepare[Alg[_[_, _, _, _, _]]](service: Service[Alg])(
        endpoint: Endpoint[service.Operation, _, _, _, _, _]
    ): Client => Client =
      prepareWithHints(service.hints, endpoint.hints)
  }

}
