package smithy4s.zio.http

import smithy4s.example.*
import zio.http.{Request, Status}
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}
import zio.{Ref, Scope, Task, ZIO}

/**
 * Tests to verify the error handling behavior in RouterBuilder,
 * specifically testing the double error handler wrapping pattern:
 * - First handler: transforms service errors (non-contract → contract error)
 * - Second handler: handles middleware errors, but preserves contract errors
 */
object RouterBuilderErrorHandlingSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("RouterBuilder error handling")(
      test(
        "error transformation to contract error is applied once and preserved"
      ) {
        case class CustomDatabaseError(msg: String)
            extends RuntimeException(msg)

        for {
          // Counter to track how many times the error mapper is called
          transformCount <- Ref.make(0)

          failingService: PizzaAdminService[Task] =
            new PizzaAdminService.Default[Task](
              ZIO.fail(CustomDatabaseError("database connection failed"))
            ) {
              override def health(query: Option[String]): Task[HealthResponse] =
                ZIO.fail(CustomDatabaseError("database connection failed"))
            }

          // CORRECT pattern: Transform to a Smithy contract error
          // Contract errors are preserved by the second error handler pass
          errorMapper: PartialFunction[Throwable, Task[Throwable]] = {
            case CustomDatabaseError(msg) =>
              transformCount
                .update(_ + 1)
                .as(
                  UnknownServerError(
                    UnknownServerErrorCode.ERROR_CODE,
                    Some(s"Transformed: $msg"),
                    None
                  )
                )
          }

          routes <- SimpleRestJsonBuilder
            .routes(failingService)
            .flatMapErrors(errorMapper)
            .lift
          response <- routes.sandbox.runZIO(Request.get("/health"))
          count <- transformCount.get
        } yield assertTrue(
          response.status == Status.InternalServerError || response.status.code == 500,
          count == 1 // Transformed once; second pass preserves contract error
        )
      }
    )
}
