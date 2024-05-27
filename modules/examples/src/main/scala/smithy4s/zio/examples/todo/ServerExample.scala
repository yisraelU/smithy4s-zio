package smithy4s.zio.examples.todo

import com.comcast.ip4s.*
import example.todo.Todo
import example.todo.TodoServiceGen.serviceInstance
import smithy4s.zio.examples.todo.impl.{
  InMemoryDatabase,
  PrimaryKeyGen,
  ToDoImpl
}
import smithy4s.zio.http.SimpleRestJsonBuilder
import zio.http.{Routes, Server}
import zio.{ExitCode, URIO, ZIO, ZIOAppDefault}

object Main extends ZIOAppDefault {

  private val port: Port = Port.fromInt(8091).get

  val app: ZIO[Any, Throwable, Routes[Any, Nothing]] = {
    for {
      _ <- zio.Console.printLine(s"Starting server on http://localhost:$port")
      keyGen = PrimaryKeyGen.default()
      db <- InMemoryDatabase.make[Todo](keyGen)
      routes <- SimpleRestJsonBuilder
        .routes(ToDoImpl(db, "http://localhost/todos"))
        .lift
      app = routes.sandbox
      _ <- zio.Console.printLine(s"Starting server on http://localhost:$port")
    } yield app
  }

  override def run: URIO[Any, ExitCode] = {
    app
      .flatMap(Server.serve(_).provide(Server.defaultWithPort(port.value)))
      .exitCode
  }
}
