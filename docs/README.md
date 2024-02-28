## Smithy4s-ZIO

### Introduction
- A few small libs based off the great [Smithy4s](https://disneystreaming.github.io/smithy4s/) to enable integration with ZIO ecosystem.
#### Keep in mind this is WIP

### Credits
- This project is based completely off the [http4s](https://http4s.org/) integration in Smithy4s.

### Compliance Tests - wip
- This project is tested using the [Smithy Protocol Compliance Tests](https://smithy.io/2.0/additional-specs/http-protocol-compliance-tests.html) for the `alloy#simpleRestJson` protocol.
- Currently all Server tests pass

### Published Modules
  - Http for the [ZIO Http](https://zio.dev/http/) library
    - ZIO Http Client and Server implementations for the [`alloy#simpleRestJsonProtocol`](https://github.com/disneystreaming/alloy)
  - Prelude for the [ZIO Prelude](https://zio.dev/zio-prelude/) library   
    - Automatic derivation of the following Typeclasses for Smithy4s generated schemas 
      - Debug
      - Hash
      - Equals
  - Schema for [ZIO Schema](https://zio.dev/schema/) library - WIP
    - a Natural Transformation from Smithy4s Schema to a ZIO Schema


### Notes
- This doc assumes an understanding of Smithy and Smithy4s. For all questions related to Smithy4s and the core concepts please see the [Smithy4s](https://disneystreaming.github.io/smithy4s/) documentation and the [Smithy](https://awslabs.github.io/smithy/) documentation.


### Usage

This library is currently available for Scala binary versions 2.13 and 3.1.

To use the latest version, include the following in your `build.sbt`:

```scala
libraryDependencies ++= Seq(
  "io.github.yisraelu" %% "smithy4s-zio-http" % "@VERSION@"
)
```

The snapshot version is available via the Sonatype snapshots repository: ```@SNAPSHOT_VERSION@.```



### Http Server and Client Quickstart (borrows from Smithy4s example)   

```scala mdoc:silent
import example.hello._
import zio.{Task, ZIO,ZIOAppDefault, ExitCode, URIO}
import zio.http._
import com.comcast.ip4s._
import smithy4s.zio.http.SimpleRestJsonBuilder

object HelloWorldImpl extends HelloWorldService[Task] {
  def hello(name: String, town: Option[String]): Task[Greeting] = ZIO.succeed {
    town match {
      case None => Greeting(s"Hello $name!")
      case Some(t) => Greeting(s"Hello $name from $t!")
    }
  }
}

object Main extends ZIOAppDefault {

  val port =  Port.fromInt(9000).get
  val app: ZIO[Any, Throwable, HttpApp[Any]] = {
    for {
      _ <- zio.Console.printLine(s"Starting server on http://localhost:$port")
      routes <- SimpleRestJsonBuilder
        .routes(HelloWorldImpl)
        .lift
      app = routes.sandbox.toHttpApp
    } yield app
  }

  override def run: URIO[Any, ExitCode] = {
    app
      .flatMap(Server.serve(_).provide(Server.defaultWithPort(port.value)))
      .exitCode
  }
}
```

## Run Service

- for sbt: `sbt "example/run"`


## Client Example

You can also generate a client using smithy4s.

```scala mdoc:compile-only
import example.hello._
import smithy4s.zio.http._
import zio.http.{Client, URL}
import zio.{Scope, ZIO, ZIOAppArgs, ZIOAppDefault}

object ClientImpl extends ZIOAppDefault {

  private val helloWorldClient: ZIO[Client, Throwable, HelloWorldService[ResourcefulTask]] = {
    for {
      url <- ZIO.fromEither(URL.decode("http://localhost:9000"))
      client <- ZIO.service[Client]
      helloClient <- SimpleRestJsonBuilder(HelloWorldService)
        .client(client)
        .url(url)
        .lift
    } yield helloClient
  }

  val program = helloWorldClient.flatMap(c =>
    c.hello("Sam", Some("New York City"))
      .flatMap(greeting => zio.Console.printLine(greeting.message))
  )

  override def run: ZIO[Any & ZIOAppArgs & Scope, Any, Any] =
    program.exitCode.provide(Client.default, Scope.default)

}
```

