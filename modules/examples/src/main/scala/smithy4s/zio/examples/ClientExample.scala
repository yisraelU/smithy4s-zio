package smithy4s.zio.examples

import example.todo.TodoServiceGen
import smithy4s.zio.http.SimpleRestJsonBuilder
import zio.http.{Client, URL}
import zio.{Scope, ZIO, ZIOAppArgs, ZIOAppDefault}
object ClientExample extends ZIOAppDefault {

  private val todoClient = for {
    url <- ZIO.fromEither(URL.decode("http://localhost:8081"))
    client <- ZIO.service[Client]
    clientService <- SimpleRestJsonBuilder(TodoServiceGen)
      .client(client)
      .uri(url)
      .lift
  } yield clientService

  private val program: ZIO[Client, Throwable, Unit] = ???

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
    program.exitCode.provide(Client.default)
}
