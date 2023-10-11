package smithy4s.zio.http

import smithy4s.client._
import smithy4s.server.UnaryServerCodecs
import zio.Task
import zio.http.{Request, Response, URL}

// scalafmt: { maxColumn = 120 }
trait SimpleProtocolCodecs {
  def makeServerCodecs: UnaryServerCodecs.Make[Task, Request, Response]
  def makeClientCodecs(baseUri: URL): UnaryClientCodecs.Make[Task, Request, Response]

}
