package smithy4s.zio.compliancetests

import smithy4s.http.HttpMediaType
import smithy4s.kinds.FunctorAlgebra
import smithy4s.schema.Schema
import smithy4s.{Service, ShapeTag}
import zio.Task
import zio.http.HttpApp

/* A construct encapsulating the action of turning an http4s route into
 * an an algebra
 */
trait ReverseRouter {
  type Protocol
  def protocolTag: ShapeTag[Protocol]
  def expectedResponseType(schema: Schema[_]): HttpMediaType

  def reverseRoutes[Alg[_[_, _, _, _, _]]](
      routes: HttpApp[Any],
      host: Option[String] = None
  )(implicit service: Service[Alg]): Task[FunctorAlgebra[Alg, ResourcefulTask]]
}
