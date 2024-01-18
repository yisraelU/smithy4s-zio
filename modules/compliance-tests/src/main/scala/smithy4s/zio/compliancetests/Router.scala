package smithy4s.zio.compliancetests

import smithy4s.{Service, ShapeTag}
import smithy4s.kinds.FunctorAlgebra
import zio.Task

/* A construct encapsulating the action of turning an algebra implementation into
 * an http route (modelled using Http4s, but could be backed by any other library
 * by means of proxyfication)
 */
trait Router {
  type Protocol
  def protocolTag: ShapeTag[Protocol]

  def routes[Alg[_[_, _, _, _, _]]](alg: FunctorAlgebra[Alg, Task])(implicit
      service: Service[Alg]
  ): Task[HttpRoutes]
}
