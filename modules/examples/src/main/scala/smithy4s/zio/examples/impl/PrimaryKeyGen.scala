package smithy4s.zio.examples.impl

import zio.{Task, ZIO}

trait PrimaryKeyGen {

  def generate(): Task[String]
}

object PrimaryKeyGen {

  def default(): PrimaryKeyGen = () =>
    ZIO.succeed(java.util.UUID.randomUUID().toString)
}
