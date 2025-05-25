package smithy4s.zio.examples

import zio.{Scope, ZIO}

package object todo {

  org.http4s.Uri
  type ResourcefulTask[A] = ZIO[Scope, Throwable, A]
}
