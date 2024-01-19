/*
package smithy4s.zio.http



import cats.implicits.*
import fs2.Collector
import smithy4s.Hints
import smithy4s.example.hello.*
import zio.{Scope, Task, ZIO}
import zio.http.Request.post
import zio.http.{Body, HttpApp, Method, Request, URL}
import zio.test.{Assertion, Spec, TestEnvironment, TestResult, ZIOSpecDefault}

object ServerEndpointMiddlewareSpec extends ZIOSpecDefault {

  private implicit val greetingEq: Eq[Greeting] = Eq.fromUniversalEquals
  private implicit val throwableEq: Eq[Throwable] = Eq.fromUniversalEquals
  final class MiddlewareException
      extends RuntimeException(
        "Expected to recover via flatmapError or mapError"
      )

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("middleware testing") {
  test("server - middleware can throw and mapped / flatmapped") {
    val middleware = new ServerEndpointMiddleware.Simple[Task]() {
      def prepareWithHints(
          serviceHints: Hints,
          endpointHints: Hints
      ): HttpApp[Any] => HttpApp[Any] = { inputApp =>
        HttpApp[Any] { _ => ZIO.fail(new MiddlewareException }
      }
    }
    def runOnService(service: HttpRoutes): ZIO[Any, Nothing, TestResult] = {
      service.sandbox.toHttpApp.runZIO(post( "/bob", Body.empty))
        .map(res =>  zio.test.assert(res.status.code)(Assertion.equalTo(599)))
    }


    val pureCheck = runOnService(
      SimpleRestJsonBuilder(HelloImpl)
        .routes(HelloImpl)
        .middleware(middleware)
        .flatMapErrors { case _: MiddlewareException =>
          Task.pure(SpecificServerError())
        }
        .make
        .toOption
        .get
    )
    val throwCheck = runOnService(
      SimpleRestJsonBuilder
        .routes(HelloImpl)
        .middleware(middleware)
        .mapErrors { case _: MiddlewareException =>
          throw SpecificServerError()
        }
        .make
        .toOption
        .get
    )
    List(throwCheck, pureCheck).combineAll
  ???
  }

  test("server - middleware can catch spec error") {
    val catchSpecErrorMiddleware = new ServerEndpointMiddleware.Simple[Task]() {
      def prepareWithHints(
          serviceHints: Hints,
          endpointHints: Hints
      ): HttpApp[Any] => HttpApp[Any] = { inputApp =>
        HttpApp[Any] { req =>
          inputApp(req).handleError { case _: SpecificServerError =>
            Response[Any()
          }
        }
      }
    }

    SimpleRestJsonBuilder
      .routes(new HelloWorldService[Task] {
        def hello(name: String, town: Option[String]): Task[Greeting] =
          ZIO.fail(
            SpecificServerError(Some("to be caught in middleware"))
          )
      })
      .middleware(catchSpecErrorMiddleware)
      .make
      .toOption
      .get
      .apply(Request[](Method.POST, Uri.unsafeFromString("/bob")))
      // would be 599 w/o the middleware
      .flatMap(res => OptionT.pure(expect.eql(res.status.code, 200)))
      .getOrElse(
        failure("unable to run request")
      )
  },

  test("server - middleware is applied") {
    serverMiddlewareTest(
      shouldFailInMiddleware = true,
      Request(Method.POST, Uri.unsafeFromString("/bob")),
      response =>
        IO.pure(expect.eql(response.status, Status.InternalServerError))
    )
  },

  test(
    "server - middleware allows passing through to underlying implementation"
  ) {
    serverMiddlewareTest(
      shouldFailInMiddleware = false,
      Request(Method.POST, Uri.unsafeFromString("/bob")),
      response => {
        response.body.compile
          .to(Collector.supportsArray(Array))
          .map(new String(_))
          .map { body =>
            expect.eql(response.status, Status.Ok) &&
            expect.eql(body, """{"message":"Hello, bob"}""")
          }
      }
    )
  },

  test("client - middleware is applied") {
    clientMiddlewareTest(
      shouldFailInMiddleware = true,
      service =>
        service.hello("bob").attempt.map { result =>
          expect.eql(result, Left(new GenericServerError(Some("failed"))))
        }
    )
  }
      ,

  test("client - send request through middleware") {
    clientMiddlewareTest(
      shouldFailInMiddleware = false,
      service =>
        service.hello("bob").attempt.map { result =>
          expect.eql(result, Right(Greeting("Hello, bob")))
        }
    )
  }
    }

  private def serverMiddlewareTest(
      shouldFailInMiddleware: Boolean,
      request: Request,
      expect: Response[IO] => IO[Expectations]
  )(implicit pos: SourceLocation): IO[Expectations] = {
    val service =
      SimpleRestJsonBuilder
        .routes(HelloImpl)
        .middleware(
          new TestServerMiddleware(shouldFail = shouldFailInMiddleware)
        )
        .make
        .toOption
        .get

    service(request)
      .flatMap(res => OptionT.liftF(expect(res)))
      .getOrElse(
        failure("unable to run request")
      )
  }

  private def clientMiddlewareTest(
      shouldFailInMiddleware: Boolean,
      expect: HelloWorldService[IO] => IO[Expectations]
  ): IO[Expectations] = {
    val serviceNoMiddleware: HttpApp[Any] =
      SimpleRestJsonBuilder
        .routes(HelloImpl)
        .make
        .toOption
        .get
        .orNotFound

    val client: HelloWorldService[IO] = {
      val http4sClient = Client.fromHttpApp(serviceNoMiddleware)
      SimpleRestJsonBuilder(HelloWorldService)
        .client(http4sClient)
        .middleware(
          new TestClientMiddleware(shouldFail = shouldFailInMiddleware)
        )
        .make
        .toOption
        .get
    }

    expect(client)
  }

  private object HelloImpl extends HelloWorldService[IO] {
    def hello(name: String, town: Option[String]): IO[Greeting] = IO.pure(
      Greeting(s"Hello, $name")
    )
  }

  private final class TestServerMiddleware(shouldFail: Boolean)
      extends ServerEndpointMiddleware.Simple[IO] {
    def prepareWithHints(
        serviceHints: Hints,
        endpointHints: Hints
    ): HttpApp[Any] => HttpApp[Any] = { inputApp =>
      HttpApp[Any] { request =>
        val hasTag: (Hints, String) => Boolean = (hints, tagName) =>
          hints.get[smithy.api.Tags].exists(_.value.contains(tagName))
        // check for tags in hints to test that proper hints are sent into the prepare method
        if (
          hasTag(serviceHints, "testServiceTag") &&
          hasTag(endpointHints, "testOperationTag")
        ) {
          if (shouldFail) {
            IO.raiseError(new GenericServerError(Some("failed")))
          } else {
            inputApp(request)
          }
        } else {
          IO.raiseError(new Exception("didn't find tags in hints"))
        }
      }
    }
  }

  private final class TestClientMiddleware(shouldFail: Boolean)
      extends ClientEndpointMiddleware.Simple[IO] {
    def prepareWithHints(
        serviceHints: Hints,
        endpointHints: Hints
    ): Client[IO] => Client[IO] = { inputClient =>
      Client[IO] { request =>
        val hasTag: (Hints, String) => Boolean = (hints, tagName) =>
          hints.get[smithy.api.Tags].exists(_.value.contains(tagName))
        // check for tags in hints to test that proper hints are sent into the prepare method
        if (
          hasTag(serviceHints, "testServiceTag") &&
          hasTag(endpointHints, "testOperationTag")
        ) {
          if (shouldFail) {
            Resource.eval(IO.raiseError(new GenericServerError(Some("failed"))))
          } else {
            inputClient.run(request)
          }
        } else {
          Resource.eval(
            IO.raiseError(new Exception("didn't find tags in hints"))
          )
        }
      }
    }
  }

}
 */
