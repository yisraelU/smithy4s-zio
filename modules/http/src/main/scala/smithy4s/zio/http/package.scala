package smithy4s.zio

import smithy4s.Endpoint
import smithy4s.http.{CaseInsensitive, HttpMethod}
import zio.http.Method._
import zio.http._

package object http {
  type ServerEndpointMiddleware = Endpoint.Middleware[EHttpApp]
  type ClientEndpointMiddleware = Endpoint.Middleware[Client]
}
