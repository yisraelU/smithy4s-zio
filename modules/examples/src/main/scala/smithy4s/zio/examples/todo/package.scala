package smithy4s.zio.examples

import zio.{Scope, ZIO}

package object todo {

  type ResourcefulTask[A] = ZIO[Scope, Throwable, A]
}
