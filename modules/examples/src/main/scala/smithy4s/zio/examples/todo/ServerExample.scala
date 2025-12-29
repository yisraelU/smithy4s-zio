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
import zio.http.Server
import zio.{Scope, ZIO, ZIOAppArgs, ZIOAppDefault}

object Main extends ZIOAppDefault {

  private val port: Port = Port.fromInt(8091).get

  val app = {
    for {
      _ <- zio.Console.printLine(s"Starting server on http://localhost:$port")
      keyGen = PrimaryKeyGen.default()
      db <- InMemoryDatabase.make[Todo](keyGen)
      // routesAppWith automatically builds and sandboxes routes, ready for Server.serve
      routes <- ZIO.fromEither(
        SimpleRestJsonBuilder
          .routesAppWith(ToDoImpl(db, "http://localhost/todos")) { builder =>
            builder // can add middleware, error handling, etc. here
          }
      )
      _ <- zio.Console.printLine(s"Server ready at http://localhost:$port")
    } yield routes
  }

  override def run: ZIO[Any & ZIOAppArgs & Scope, Throwable, Nothing] = {
    app
      .flatMap(Server.serve(_).provide(Server.defaultWithPort(port.value)))
  }
}
