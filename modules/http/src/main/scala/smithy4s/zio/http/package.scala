package smithy4s.zio

import smithy4s.Endpoint
import smithy4s.http.PathParams
import zio.{Scope, Task, ZIO}
import zio.http.*

package object http {
  type ResourcefulTask[Output] = ZIO[Scope, Throwable, Output]
  type HttpRoutes = Routes[Any, Throwable]
  type ClientEndpointMiddleware = Endpoint.Middleware[Client]
  type ServerEndpointMiddleware = Endpoint.Middleware[HttpRoutes]
  type SimpleHandler = Request => Task[Response]

  private val pathParamsKey: String = "x-smithy4s-path-params"

  private def serializePathParams(pathParams: PathParams): String = {
    pathParams.map { case (key, value) => s"$key=$value" }.mkString("&")
  }

  private def deserializePathParams(pathParamsString: String): PathParams = {
    pathParamsString
      .split("&")
      .filterNot(_.isEmpty)
      .map { param =>
        {
          param.split("=", 2) match {
            case Array(key, value) => key -> value
            case Array(k)          => (k, "")
            case _                 =>
              throw new Exception(
                s"Invalid path params string: $pathParamsString"
              )
          }
        }
      }
      .toMap

  }
  def tagRequest(req: Request, pathParams: PathParams): Request = {
    val serializedPathParams = serializePathParams(pathParams)
    req.addHeader(Header.Custom(pathParamsKey, serializedPathParams))
  }
  def lookupPathParams(req: Request): (Request, Option[PathParams]) = {
    val pathParamsString = req.headers.get(pathParamsKey)
    (
      req.removeHeader(pathParamsKey),
      pathParamsString.map(deserializePathParams)
    )
  }
}
