package smithy4s.zio.examples.todo

import example.todo.TodoServiceGen
import smithy4s.zio.http.SimpleRestJsonBuilder
import zio.http.{Client, URL}
import zio.{Scope, ZIO, ZIOAppArgs, ZIOAppDefault}
object ClientExample extends ZIOAppDefault {

  private val client = {
    for {
      url <- ZIO.fromEither(URL.decode("http://localhost:8091"))
      client <- ZIO.service[Client]
      clientService <- SimpleRestJsonBuilder(TodoServiceGen)
        .client(client)
        .uri(url)
        .lift
    } yield clientService
  }

  private val program: ZIO[Scope & Client, Throwable, Unit] =
    for {
      client <- client
      _ <- client.healthCheck().debug
      todo <- client
        .createTodo(
          title = "My todo",
          order = Some(1),
          description = Some("My description")
        )
        .debug
      _ <- client
        .getTodo(
          id = todo.id
        )
        .debug
      _ <- client
        .updateTodo(
          id = todo.id,
          title = Some("My todo"),
          order = Some(1),
          description = Some("My description"),
          completed = Some(true)
        )
        .debug
      _ <- client
        .deleteTodo(
          id = todo.id
        )
        .debug
      _ <- client.deleteAll().debug
    } yield ()

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
    program.exitCode.provide(Client.default, Scope.default)
}
