package smithy4s.zio.http.internal

import smithy4s.client.UnaryLowLevelClient
import smithy4s.zio.http.ResourcefulTask
import zio.http.{Client, Request, Response}
import zio.{RIO, Scope}

private[http] object ZHttpToSmithy4sClient {

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
