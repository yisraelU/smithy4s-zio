package smithy4s.zio.http

import smithy4s.example.{RecursiveInputService, RecursiveInput}
import zio.Scope
import zio.http.Client
import zio.test.*

// This is a non-regression test for https://github.com/disneystreaming/smithy4s/issues/181
// CREDITS to  https://github.com/disneystreaming/smithy4s/blob/series/0.18/modules/http4s/test/src/smithy4s/http4s/RecursiveInputSpec.scala
object RecursiveInputSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("recursive input test")(
      test("simpleRestJson works with recursive input operations") {
        val res = {
          for {
            client <- Client.default.build
            x <- SimpleRestJsonBuilder
              .apply(RecursiveInputService)
              .client(client.get)
              .lift
          } yield x
        }.provide(Scope.default)

        assertZIO(
          res
            .map(_.recursiveInputOperation(Some(RecursiveInput(None))))
            .as(true)
        )(Assertion.isTrue)
      }
    )
}
