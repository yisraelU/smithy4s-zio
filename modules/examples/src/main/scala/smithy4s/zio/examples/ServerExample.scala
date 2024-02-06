package smithy4s.zio.examples

import example.todo.Todo
import example.todo.TodoServiceGen.serviceInstance
import smithy4s.zio.examples.impl.{InMemoryDatabase, PrimaryKeyGen, ToDoImpl}
import smithy4s.zio.http.SimpleRestJsonBuilder
import zio.{ExitCode, URIO, ZIO, ZIOAppDefault}
import com.comcast.ip4s.*
import zio.http.{HttpApp, Server}

object Main extends ZIOAppDefault {

  private val port: Port = Port.fromInt(8091).get

  val app: ZIO[Any, Throwable, HttpApp[Any]] = {
    for {
      _ <- zio.Console.printLine(s"Starting server on http://localhost:$port")
      keyGen = PrimaryKeyGen.default()
      db <- InMemoryDatabase.make[Todo](keyGen)
      routes <- SimpleRestJsonBuilder
        .routes(ToDoImpl(db, "http://localhost/todos"))
        .lift
      app = routes.sandbox.toHttpApp
      _ <- zio.Console.printLine(s"Starting server on http://localhost:$port")
    } yield app
  }

  override def run: URIO[Any, ExitCode] = {
    app
      .flatMap(Server.serve(_).provide(Server.defaultWithPort(port.value)))
      .exitCode
  }
}
