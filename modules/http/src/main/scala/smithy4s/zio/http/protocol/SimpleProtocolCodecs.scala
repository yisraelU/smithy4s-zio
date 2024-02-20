package smithy4s.zio.http.protocol

import smithy4s.client.*
import smithy4s.server.UnaryServerCodecs
import smithy4s.zio.http.ResourcefulTask
import zio.Task
import zio.http.{Request, Response, URL}

// scalafmt: { maxColumn = 120 }
trait SimpleProtocolCodecs {
  def makeClientCodecs(baseUri: URL): UnaryClientCodecs.Make[ResourcefulTask, Request, Response]
  def makeServerCodecs: UnaryServerCodecs.Make[Task, Request, Response]

}
