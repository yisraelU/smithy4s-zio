package smithy4s.zio.examples.impl

import smithy4s.hello.{Greeting, HelloWorldService, Message}
import zio.{Task, ZIO}
object HelloWorldImpl extends HelloWorldService[Task] {
  def hello(name: String, town: Option[String]): Task[Greeting] = {
    for {
      _ <- ZIO.succeed(println(s"Hello $name from $town!"))
      greeting <- ZIO.succeed(town match {
        case None    => Greeting(s"Hello $name!")
        case Some(t) => Greeting(s"Hello $name from $t!")
      })
    } yield greeting
  }

  def healthCheck(): Task[Message] =
    ZIO.attempt(println("All good")) *> ZIO.succeed(Message("OK"))
}
