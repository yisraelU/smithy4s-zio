package smithy4s.zio

import cats.effect.SyncIO
import org.typelevel.vault.Key
import smithy4s.Endpoint
import smithy4s.http.{CaseInsensitive, HttpMethod, PathParams}
import zio.Task
import zio.http.Method._
import zio.http._

package object http {
  type ServerEndpointMiddleware = Endpoint.Middleware[EHttpApp]
  type ClientEndpointMiddleware = Endpoint.Middleware[Client]

  private val pathParamsKey: String =
    Key.newKey[SyncIO, PathParams].unsafeRunSync().hashCode().toString

  private def serializePathParams(pathParams: PathParams): String = {
    pathParams.map { case (key, value) => s"$key=$value" }.mkString("&")
  }

  private def deserializePathParams(pathParamsString: String): PathParams = {
    pathParamsString
      .split("&")
      .map { param =>
        val Array(key, value) = param.split("=")
        key -> value
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
