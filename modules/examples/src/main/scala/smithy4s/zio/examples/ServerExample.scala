package smithy4s.zio.examples

import example.todo.Todo
import example.todo.TodoServiceGen.serviceInstance
import smithy4s.zio.examples.impl.{InMemoryDatabase, PrimaryKeyGen, ToDoImpl}
import smithy4s.zio.http.SimpleRestJsonBuilder
import zio.http.Server
import zio.{Scope, ZIO, ZIOAppArgs, ZIOAppDefault}

object Main extends ZIOAppDefault {

  private val port = 8081

  val program: ZIO[Any, Throwable, Nothing] = {
    for {
      _ <- zio.Console.printLine(s"Starting server on http://localhost:$port")
      keyGen = PrimaryKeyGen.default()
      db <- InMemoryDatabase.make[Todo](keyGen)
      app <- SimpleRestJsonBuilder
        .routes(ToDoImpl(db, "http://localhost/todos"))
        .lift
      server <- Server.serve(app.withDefaultErrorResponse)
    } yield server
  }.provide(Server.defaultWithPort(port))

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = {
    program.exitCode
  }
}
