package smithy4s.zio.examples

import smithy4s.hello.{Greeting, HelloWorldService}
import smithy4s.zio.http.SimpleRestJsonBuilder
import zio.http.{Client, URL}
import zio.{ExitCode, Scope, ZIO, ZIOAppArgs, ZIOAppDefault}
object ClientExample extends ZIOAppDefault {

  private val helloWorldClient = for {
    url <- ZIO.fromEither(URL.decode("http://localhost:8080"))
    client <- ZIO.service[Client]
    clientService <- SimpleRestJsonBuilder(HelloWorldService)
      .client(client)
      .uri(url)
      .lift
  } yield clientService

  private val program: ZIO[Client, Throwable, Greeting] =
    helloWorldClient.flatMap { client =>
      client.hello("World", None)
    }

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
    program.exitCode.provide(Client.default)
}
