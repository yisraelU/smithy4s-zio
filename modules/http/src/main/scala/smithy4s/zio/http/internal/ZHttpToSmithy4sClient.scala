package smithy4s.zio.http.internal

import smithy4s.client.UnaryLowLevelClient
import zio.http.{Client, Request, Response}
import zio.{RIO, Scope, ZIO}

private[http] object ZHttpToSmithy4sClient {

  type ResourcefulTask[Output] = ZIO[Scope, Throwable, Output]

  def apply(
      client: Client
  ): UnaryLowLevelClient[ResourcefulTask, Request, Response] = {
    new UnaryLowLevelClient[ResourcefulTask, Request, Response] {
      def run[Output](request: Request)(
          cb: Response => RIO[Scope, Output]
      ): ResourcefulTask[Output] =
        client.request(request).flatMap(cb)
    }
  }
}
