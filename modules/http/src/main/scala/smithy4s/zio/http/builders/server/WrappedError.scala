package smithy4s.zio.http.builders.server

final case class WrappedError(throwable: Throwable) extends Throwable
