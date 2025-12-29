## Smithy4s-ZIO

### Introduction
- A few small libs based off the great [Smithy4s](https://disneystreaming.github.io/smithy4s/) to enable integration with ZIO ecosystem.
#### Keep in mind this is WIP

### Credits
- This project is based completely off the [http4s](https://http4s.org/) integration in Smithy4s.

### Protocol Compliant
- This library is protocol compliant with the [Alloy#SimpleRestJson](https://github.com/disneystreaming/alloy) protocol
- To read more about protocol in this context please see [What is a Protocol](https://disneystreaming.github.io/smithy4s/docs/protocols/definition)


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
      - Ord (total ordering)
  - Schema for the [ZIO Schema](https://zio.dev/zio-schema/) library
  - Schema for [ZIO Schema](https://zio.dev/zio-schema/) library
    - Conversion from Smithy4s Schema to ZIO Schema
      - Access to ZIO Schema codec ecosystem (Protobuf, Avro, JSON, Thrift, MessagePack)
      - Schema validation, migrations, and introspection


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


Below is a quick example of smithy4s in action.
This page does not provide much explanation or detail. For more information on various aspects of smithy4s, read through the other sections of this documentation site.


This section will get you started with a simple `sbt` module that enables smithy4s code generation.

### project/plugins.sbt

Add the `smithy4s-sbt-codegen` plugin to your build.

```scala
addSbtPlugin("com.disneystreaming.smithy4s" % "smithy4s-sbt-codegen" % "<version>")
```

### build.sbt

Enable the plugin in your project, add the smithy and zio-http dependencies.

```scala
import smithy4s.codegen.Smithy4sCodegenPlugin

val example = project
  .in(file("modules/example"))
  .enablePlugins(Smithy4sCodegenPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "io.github.yisraelu" %% "smithy4s-zio-http" % "@VERSION@"
    )
  )
```

## Smithy content

Now is the time to add some Smithy shapes to see what code generation can do for you. Following the setup above, the location for the Smithy content will change depending on what build tool you used.

Now let's define an API in Smithy. Create the following file:

- You'll write in `modules/example/src/main/smithy/ExampleService.smithy`.

And add the content below:

```kotlin
namespace smithy4s.hello

use alloy#simpleRestJson

@simpleRestJson
service HelloWorldService {
  version: "1.0.0",
  operations: [Hello]
}

@http(method: "POST", uri: "/{name}", code: 200)
operation Hello {
  input: Person,
  output: Greeting
}

structure Person {
  @httpLabel
  @required
  name: String,

  @httpQuery("town")
  town: String
}

structure Greeting {
  @required
  message: String
}
```

The Scala code corresponding to this smithy file will be generated the next time you compile your project.

## Using the generated code

Now, let's use the generated code by the service. You need to create a scala file at the following location:

-  `modules/example/src/main/scala/Main.scala`

Implement your service by extending the generated Service trait. Wire up routes into server.


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
  val app: Task[Routes[Any, Response]] = {
    for {
      _ <- zio.Console.printLine(s"Starting server on http://localhost:$port")
      // liftApp automatically sandboxes the routes, making them ready to serve
      routes <- SimpleRestJsonBuilder
        .routes(HelloWorldImpl)
        .liftApp
    } yield routes
  }

  override def run: URIO[Any, ExitCode] = {
    app
      .flatMap(Server.serve(_).provide(Server.defaultWithPort(port.value)))
      .exitCode
  }
}
```

## Alternative DSL Style

You can also use the builder-style DSL for more declarative configuration:

```scala mdoc:compile-only
import smithy4s.zio.http.SimpleRestJsonBuilder._
import example.hello._
import zio.{Task, ZIO, ZIOAppDefault, ExitCode, URIO}
import zio.http._
import com.comcast.ip4s._

object Main extends ZIOAppDefault {

  val port = Port.fromInt(9000).get

  val app: Task[Routes[Any, Response]] = {
    for {
      _ <- zio.Console.printLine(s"Starting server on http://localhost:$port")
      // routesAppWith builds and sandboxes routes in one call
      routes <- ZIO.fromEither(
        routesAppWith(HelloWorldImpl) { builder =>
          builder
            // .middleware(loggingMiddleware)
            // .mapErrors { case e: MyError => MappedError(e) }
            // .onError { case e => logError(e) }
        }
      )
    } yield routes
  }

  override def run: URIO[Any, ExitCode] = {
    app
      .flatMap(Server.serve(_).provide(Server.defaultWithPort(port.value)))
      .exitCode
  }
}
```

The DSL methods are:
- `routesWith(impl) { builder => ... }` - Returns `Either[Error, HttpRoutes]`
- `routesAppWith(impl) { builder => ... }` - Returns `Either[Error, Routes[Any, Response]]` (sandboxed, ready for `Server.serve`)

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

## ZIO Schema Integration

The `smithy4s-zio-schema` module provides seamless integration with [ZIO Schema](https://zio.dev/zio-schema/), enabling smithy4s-generated types to access the entire ZIO Schema ecosystem including codecs, validation, migrations, and more.

### Benefits

Converting Smithy4s schemas to ZIO Schemas unlocks:

- **Binary Codecs**: Protobuf, Avro, Thrift, MessagePack
- **JSON Codecs**: Integration with zio-json
- **Validation**: Field-level and cross-field validation
- **Schema Migrations**: Version management and evolution
- **Introspection**: Runtime schema inspection and diffing
- **Pretty Printing**: Human-readable schema descriptions

### Setup

Add the schema module to your `build.sbt`:

```scala
libraryDependencies ++= Seq(
  "io.github.yisraelu" %% "smithy4s-zio-schema" % "@VERSION@",
  "dev.zio" %% "zio-schema-json" % "1.7.6",      // For JSON codec
  "dev.zio" %% "zio-schema-protobuf" % "1.7.6"   // For Protobuf codec
)
```

### Basic Usage

```scala
import smithy4s.zio.schema._
import zio.schema.{Schema => ZSchema}
import zio.schema.codec.{JsonCodec, ProtobufCodec}

// Your smithy4s-generated type
case class Person(name: String, age: Int, email: Option[String])
object Person {
  val smithy4sSchema: smithy4s.Schema[Person] = /* generated by smithy4s */

  // Convert to ZIO Schema using the .toZSchema extension method
  implicit val zioSchema: ZSchema[Person] = smithy4sSchema.toZSchema

  // Derive codecs from the ZIO Schema
  val jsonCodec = JsonCodec.schemaBasedBinaryCodec(zioSchema)
  val protobufCodec = ProtobufCodec.protobufCodec(zioSchema)
}

// Use the codecs
val person = Person("Alice", 30, Some("alice@example.com"))

// JSON encoding/decoding
val jsonBytes = Person.jsonCodec.encode(person)
val decoded = Person.jsonCodec.decode(jsonBytes) // Either[String, Person]

// Protobuf encoding/decoding
val protobufBytes = Person.protobufCodec.encode(person)
val decodedProto = Person.protobufCodec.decode(protobufBytes)
```

### Complete Example

See the full working example at [ZSchemaExample.scala](../modules/examples/src/main/scala/smithy4s/zio/examples/zschema/ZSchemaExample.scala) which demonstrates:

- JSON and Protobuf codec usage
- Collections support
- Schema introspection
- Roundtrip serialization

Run it with: `sbt "examples/runMain smithy4s.zio.examples.zschema.ZSchemaExample"`

### Supported Schema Types

The conversion supports all smithy4s schema types:

- ✅ **Primitives**: int, string, boolean, long, short, byte, double, float, bigint, bigdecimal
- ✅ **Smithy Types**: Blob, Timestamp, Document, ShapeId, UUID
- ✅ **Collections**: List, Set, Vector, IndexedSeq
- ✅ **Maps**: Map[K, V]
- ✅ **Structures**: Case classes with any number of fields
- ✅ **Unions**: Sealed traits / ADTs
- ✅ **Enumerations**: Both string and int enums
- ✅ **Options**: Optional fields
- ✅ **Bijections**: Newtypes and custom transformations
- ✅ **Refinements**: Constrained types
- ✅ **Recursive Types**: Self-referencing schemas

### Implementation Details

**Structs**: Converted using ZIO Schema's `GenericRecord` with transformations to preserve type safety at the API boundary.

**Unions**: Represented as tagged tuples `(tag: String, value: DynamicValue)` where `DynamicValue` is ZIO Schema's runtime-typed value representation.

**Performance**: The conversion adds minimal overhead. For codec operations (serialization/deserialization), the schema conversion cost is negligible compared to the encoding/decoding work.

### ZIO Prelude Typeclasses

The prelude module also provides automatic derivation of ZIO Prelude typeclasses:

```scala
import smithy4s.zio.prelude._
import zio.prelude.{Debug, Equal, Hash, Ord}

// Derive typeclasses from smithy4s schemas
implicit val personDebug: Debug[Person] = SchemaVisitorDebug.fromSchema(Person.schema)
implicit val personEqual: Equal[Person] = SchemaVisitorEqual.fromSchema(Person.schema)
implicit val personHash: Hash[Person] = SchemaVisitorHash.fromSchema(Person.schema)
implicit val personOrd: Ord[Person] = SchemaVisitorOrd.fromSchema(Person.schema)

// Now you can use ZIO Prelude operations
val p1 = Person("Alice", 30, Some("alice@example.com"))
val p2 = Person("Bob", 25, None)

Debug[Person].debug(p1)           // Pretty print
Equal[Person].equal(p1, p2)       // Equality check
Hash[Person].hash(p1)             // Hash code
Ord[Person].compare(p1, p2)       // Total ordering
```

