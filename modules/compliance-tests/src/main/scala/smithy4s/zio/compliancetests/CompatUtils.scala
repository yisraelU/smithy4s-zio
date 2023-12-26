


package smithy4s.zio.compliancetests

import cats.{Applicative, MonadThrow, Monoid}

abstract class CompatUtils[F[_]: MonadThrow] {
  def raiseError[A](err: Throwable): F[A] =
    MonadThrow[F].raiseError(err)

  implicit def monoid[A: Monoid]: Monoid[F[A]] = Applicative.monoid[F, A]
}
