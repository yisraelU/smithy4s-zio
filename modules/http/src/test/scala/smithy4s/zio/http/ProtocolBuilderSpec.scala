package smithy4s.zio.http

import alloy.SimpleRestJson
import smithy4s.example.{PizzaAdminServiceGen, WeatherGen}
import smithy4s.zio.http.builders.client.ClientBuilder
import zio.http.Client
import zio.test.{Assertion, Spec, TestEnvironment, ZIOSpecDefault, assertZIO}
import zio.{Scope, ZEnvironment, ZIO, ZLayer}

object ProtocolBuilderSpec extends ZIOSpecDefault {

  private val client = Client.default

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("protocol builder checks")(
      test(
        "SimpleProtocolBuilder (client) fails when the protocol is not present"
      ) {
        val result: ZIO[Any, Throwable, ZEnvironment[
          ClientBuilder[WeatherGen, SimpleRestJson]
        ]] = ZLayer
          .fromFunction(SimpleRestJsonBuilder(WeatherGen).client(_))
          .build
          .provide(client, Scope.default)

        assertZIO(result.map(_.get.make))(Assertion.isLeft)
      },
      test(
        "SimpleProtocolBuilder (client) succeeds when the protocol is present"
      ) {
        val result = ZLayer
          .fromFunction(SimpleRestJsonBuilder(PizzaAdminServiceGen).client(_))
          .build
          .provide(client, Scope.default)

        assertZIO(result.map(_.get.make))(Assertion.isRight)
      }
    )

}
