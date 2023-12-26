package smithy4s.zio.http

import smithy4s.example.guides.auth.{
  HealthCheckOutput,
  HelloWorldAuthService,
  HelloWorldAuthServiceGen,
  World
}
import smithy4s.example.{
  HealthResponse,
  PizzaAdminService,
  PizzaAdminServiceGen,
  UnknownServerError,
  UnknownServerErrorCode
}
import smithy4s.kinds.PolyFunction5
import smithy4s.Service
import zio.http.Request
import zio.{IO, Scope, Task, ZIO}
import zio.test.{
  Assertion,
  Spec,
  TestEnvironment,
  ZIOSpecDefault,
  assertTrue,
  assertZIO
}

object ServiceBuilderHttp4sSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("")(
      test("Capable of altering the URI path of an endpoint") {
        val serviceImpl: HelloWorldAuthService[Task] =
          new HelloWorldAuthService[Task] {
            override def sayWorld(): Task[World] = ZIO.succeed(World("hello"))

            override def healthCheck(): Task[HealthCheckOutput] = ???
          }

        val builder = Service.Builder.fromService(HelloWorldAuthService)

        val mapper = new PolyFunction5[
          HelloWorldAuthServiceGen.Endpoint,
          HelloWorldAuthServiceGen.Endpoint
        ] {
          def apply[I, E, O, SI, SO](
              endpoint: HelloWorldAuthServiceGen.Endpoint[I, E, O, SI, SO]
          ): HelloWorldAuthServiceGen.Endpoint[I, E, O, SI, SO] = {
            if (endpoint.name == "SayWorld") {
              endpoint.mapSchema(
                _.withHints(
                  smithy.api.Http(
                    method = smithy.api.NonEmptyString("GET"),
                    uri = smithy.api.NonEmptyString("/yeap"),
                    code = 200
                  ),
                  smithy.api.Readonly()
                )
              )
            } else {
              endpoint
            }
          }
        }
        val modifiedService = builder
          .mapEndpointEach(mapper)
          .build

        assertZIO(
          SimpleRestJsonBuilder(modifiedService)
            .routes(serviceImpl)
            .lift
            .map { routes =>
              routes.sandbox.toHttpApp.runZIO(Request.get("/yeap")).map {
                response =>
                  response.status.code == 200
              }
            }
        )(Assertion.anything)
      },
      test(
        "when an errorschema is removed and the service raises an error, it behaves in the same way as any other throwable"
      ) {
        val serviceImpl: PizzaAdminService[Task] =
          new PizzaAdminService.Default[Task](
            ZIO.fail(new NotImplementedError("This IO is not implemented"))
          ) {
            override def health(query: Option[String]): Task[HealthResponse] =
              ZIO.fail(
                UnknownServerError(
                  UnknownServerErrorCode.ERROR_CODE
                )
              )

          }

        val servicebuilder = Service.Builder.fromService(PizzaAdminService)
        val mapper = new PolyFunction5[
          PizzaAdminServiceGen.Endpoint,
          PizzaAdminServiceGen.Endpoint
        ] {
          def apply[I, E, O, SI, SO](
              endpoint: PizzaAdminServiceGen.Endpoint[I, E, O, SI, SO]
          ): PizzaAdminServiceGen.Endpoint[I, E, O, SI, SO] =
            endpoint.mapSchema(_.withoutError)
        }

        val modifiedService = servicebuilder
          .mapEndpointEach(mapper)
          .build

        assertZIO(
          SimpleRestJsonBuilder(modifiedService)
            .routes(serviceImpl)
            .lift
            .flatMap { routes =>
              routes.sandbox.toHttpApp.runZIO(Request.get("/health")).flatMap {
                response => response.body.asString
              }
            }
        )(Assertion.equalsIgnoreCase("{\"errorCode\":\"server.error\"}"))
      }
    )
}
