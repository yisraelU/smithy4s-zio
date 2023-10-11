package smithy4s.zio.examples

import smithy4s.UnsupportedProtocolError
import smithy4s.hello.{Greeting, HelloWorldService, Message}
import smithy4s.zio.http.SimpleRestJsonBuilder
import zio.http.{EHttpApp, Server}
import zio.{IO, Scope, Task, ZIO, ZIOAppArgs, ZIOAppDefault}

object HelloWorldImpl extends HelloWorldService[Task] {
  def hello(name: String, town: Option[String]): Task[Greeting] = ZIO.succeed {
    town match {
      case None    => Greeting(s"Hello $name!")
      case Some(t) => Greeting(s"Hello $name from $t!")
    }
  }

   def healthCheck(): Task[Message] = ZIO.succeed(Message("OK"))
}

object Routes {
  val example: IO[UnsupportedProtocolError, EHttpApp] =
    SimpleRestJsonBuilder.routes(HelloWorldImpl).lift
}

object Main extends ZIOAppDefault {

  private val port = 8080

  val program: ZIO[Any, Throwable, Nothing] = {
    for {
      _ <- zio.Console.printLine(s"Starting server on http://localhost:$port")
      app <- Routes.example
      _ <- zio.Console.printLine(s"Server started on http://localhost:$port")
      server <- Server.serve(app.withDefaultErrorResponse)
    } yield server
  }.provide(Server.defaultWithPort(8080))

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = {
    program.exitCode
  }
}
