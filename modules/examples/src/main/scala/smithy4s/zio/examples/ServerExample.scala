package smithy4s.zio.examples

import smithy4s.UnsupportedProtocolError
import smithy4s.hello.HelloWorldServiceGen.serviceInstance
import smithy4s.zio.examples.impl.HelloWorldImpl
import smithy4s.zio.http.SimpleRestJsonBuilder
import zio.http.{EHttpApp, Server}
import zio.{IO, Scope, ZIO, ZIOAppArgs, ZIOAppDefault}

object Routes {
  val example: IO[UnsupportedProtocolError, EHttpApp] =
    SimpleRestJsonBuilder.routes(HelloWorldImpl).lift
}

object Main extends ZIOAppDefault {

  private val port = 8081

  val program: ZIO[Any, Throwable, Nothing] = {
    for {
      _ <- zio.Console.printLine(s"Starting server on http://localhost:$port")
      app <- Routes.example
      server <- Server.serve(app.withDefaultErrorResponse)
    } yield server
  }.provide(Server.defaultWithPort(port))

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = {
    program.exitCode
  }
}
