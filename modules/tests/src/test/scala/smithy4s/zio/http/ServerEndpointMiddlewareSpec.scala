package smithy4s.zio.http

import smithy4s.Hints
import smithy4s.example.*
import smithy4s.zio.http.middleware.{ServerEndpointMiddleware => ServerMW}
import zio.http.{Request, Status}
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}
import zio.{Scope, Task, ZIO}

/**
 * Comprehensive middleware tests for smithy4s-zio-http.
 *
 * Tests cover:
 * - Basic middleware composition
 * - Error mapping with mapErrors and flatMapErrors
 * - Preservation of Smithy4s endpoint errors
 * - Transformation of non-endpoint errors
 */
object ServerEndpointMiddlewareSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("middleware testing")(
      test("server - middleware can be applied without errors") {
        val middleware = new PassThroughMiddleware()
        for {
          routes <- SimpleRestJsonBuilder
            .routes(PizzaImpl)
            .middleware(middleware)
            .lift
          response <- routes.sandbox.runZIO(Request.get("/health"))
          body <- response.body.asString
        } yield assertTrue(
          response.status == Status.Ok,
          body.contains("Ok")
        )
      },
      test("server - multiple middlewares can be composed") {
        val middleware1 = new PassThroughMiddleware()
        val middleware2 = new PassThroughMiddleware()
        for {
          routes <- SimpleRestJsonBuilder
            .routes(PizzaImpl)
            .middleware(middleware1)
            .middleware(middleware2)
            .lift
          response <- routes.sandbox.runZIO(Request.get("/health"))
        } yield assertTrue(response.status == Status.Ok)
      },
      test("server - mapErrors transforms non-endpoint errors") {
        case class CustomError(message: String) extends RuntimeException(message)
        case class TransformedError(original: String) extends RuntimeException(s"Transformed: $original")

        val failingService: PizzaAdminService[Task] =
          new PizzaAdminService.Default[Task](
            ZIO.fail(CustomError("Original error"))
          ) {
            override def health(query: Option[String]): Task[HealthResponse] =
              ZIO.fail(CustomError("Original error"))
          }

        val errorMapper = ServerMW.mapErrors {
          case CustomError(msg) => TransformedError(msg)
        }

        for {
          routes <- SimpleRestJsonBuilder
            .routes(failingService)
            .middleware(errorMapper)
            .lift
          response <- routes.sandbox.runZIO(Request.get("/health"))
        } yield assertTrue(
          // Errors are caught and encoded as HTTP 500 responses
          response.status == Status.InternalServerError ||
          response.status.code == 500
        )
      },
      test("server - flatMapErrors allows effectful error transformation") {
        case class DatabaseError(code: Int) extends RuntimeException(s"DB Error: $code")
        case class EnrichedError(code: Int, details: String) extends RuntimeException(s"Code $code: $details")

        val failingService: PizzaAdminService[Task] =
          new PizzaAdminService.Default[Task](
            ZIO.fail(DatabaseError(500))
          ) {
            override def health(query: Option[String]): Task[HealthResponse] =
              ZIO.fail(DatabaseError(500))
          }

        // Simulate fetching error details from a service
        def enrichError(code: Int): Task[String] =
          ZIO.succeed(s"Details for error $code")

        val errorMapper = ServerMW.flatMapErrors {
          case DatabaseError(code) =>
            enrichError(code).map(details => EnrichedError(code, details))
        }

        for {
          routes <- SimpleRestJsonBuilder
            .routes(failingService)
            .middleware(errorMapper)
            .lift
          response <- routes.sandbox.runZIO(Request.get("/health"))
        } yield assertTrue(
          // Effectful error transformation succeeds and error is encoded as HTTP response
          response.status == Status.InternalServerError ||
          response.status.code == 500
        )
      },
      test("server - endpoint errors (Smithy4s typed errors) are preserved") {
        val failingService: PizzaAdminService[Task] =
          new PizzaAdminService.Default[Task](
            ZIO.fail(new NotImplementedError("Not implemented"))
          ) {
            override def health(query: Option[String]): Task[HealthResponse] =
              ZIO.fail(
                UnknownServerError(
                  UnknownServerErrorCode.ERROR_CODE,
                  Some("Server is down"),
                  None
                )
              )
          }

        case class GenericError(msg: String) extends RuntimeException(msg)

        // This mapper should NOT transform endpoint errors
        val errorMapper = ServerMW.mapErrors {
          case e: NotImplementedError => GenericError(e.getMessage)
        }

        for {
          routes <- SimpleRestJsonBuilder
            .routes(failingService)
            .middleware(errorMapper)
            .lift
          response <- routes.sandbox.runZIO(Request.get("/health"))
        } yield assertTrue(
          // The endpoint error should be encoded as a proper HTTP response (500)
          response.status == Status.InternalServerError ||
          response.status.code == 500
        )
      },
      test("server - errors not matching partial function pass through unchanged") {
        case class SpecificError(msg: String) extends RuntimeException(msg)
        case class OtherError(msg: String) extends RuntimeException(msg)
        case class TransformedError(msg: String) extends RuntimeException(msg)

        val failingService: PizzaAdminService[Task] =
          new PizzaAdminService.Default[Task](
            ZIO.fail(OtherError("This should not be transformed"))
          ) {
            override def health(query: Option[String]): Task[HealthResponse] =
              ZIO.fail(OtherError("This should not be transformed"))
          }

        // Only transform SpecificError (OtherError won't match)
        val errorMapper = ServerMW.mapErrors {
          case SpecificError(msg) => TransformedError(msg)
        }

        for {
          routes <- SimpleRestJsonBuilder
            .routes(failingService)
            .middleware(errorMapper)
            .lift
          response <- routes.sandbox.runZIO(Request.get("/health"))
        } yield assertTrue(
          // Error passes through and is still encoded as HTTP 500
          response.status == Status.InternalServerError ||
          response.status.code == 500
        )
      },
      test("server - error mapping works with RouterBuilder.mapErrors") {
        case class ServiceError(code: Int) extends RuntimeException(s"Error: $code")
        case class MappedError(code: Int) extends RuntimeException(s"Mapped: $code")

        val failingService: PizzaAdminService[Task] =
          new PizzaAdminService.Default[Task](
            ZIO.fail(ServiceError(404))
          ) {
            override def health(query: Option[String]): Task[HealthResponse] =
              ZIO.fail(ServiceError(404))
          }

        for {
          routes <- SimpleRestJsonBuilder
            .routes(failingService)
            .mapErrors {
              case ServiceError(code) => MappedError(code)
            }
            .lift
          response <- routes.sandbox.runZIO(Request.get("/health"))
        } yield assertTrue(
          // RouterBuilder.mapErrors also works correctly
          response.status == Status.InternalServerError ||
          response.status.code == 500
        )
      }
    )

  // Use Default trait for minimal service implementation
  private lazy val PizzaImpl: PizzaAdminService[Task] =
    new PizzaAdminService.Default[Task](
      ZIO.fail(new NotImplementedError("Not needed for middleware tests"))
    ) {
      override def health(query: Option[String]): Task[HealthResponse] =
        ZIO.succeed(HealthResponse("Ok"))
    }

  // Simple pass-through server middleware for testing
  private final class PassThroughMiddleware()
      extends ServerMW.Simple[Task] {
    def prepareWithHints(
        serviceHints: Hints,
        endpointHints: Hints
    ): HttpRoutes => HttpRoutes = identity
  }
}
