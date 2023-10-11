package smithy4s.zio.http.internal

import smithy4s.client.UnaryLowLevelClient
import zio.Task
import zio.http.{Client, Request, Response}

private[http] object ZHttpToSmithy4sClient {
  def apply(
      client: Client
  ): UnaryLowLevelClient[Task, Request, Response] = {
    new UnaryLowLevelClient[Task, Request, Response] {
      def run[Output](request: Request)(
          cb: Response => Task[Output]
      ): Task[Output] =
        client.request(request).flatMap(cb)
    }
  }
}
