package smithy4s.zio

import zio.{Scope, ZIO}

package object examples {

  type ResourcefulTask[A] = ZIO[Scope, Throwable, A]
}
