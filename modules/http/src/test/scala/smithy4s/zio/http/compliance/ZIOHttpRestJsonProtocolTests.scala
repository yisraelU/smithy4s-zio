package smithy4s.zio.http.compliance

import alloy.SimpleRestJson
import smithy4s.dynamic.DynamicSchemaIndex
import smithy4s.http.HttpMediaType
import smithy4s.kinds.FunctorAlgebra
import smithy4s.schema.Schema
import smithy4s.zio.compliancetests
import smithy4s.zio.compliancetests.{
  AllowRules,
  AlloyBorrowedTests,
  HttpRoutes,
  ProtocolComplianceSuite,
  Router,
  ShouldRun
}
import smithy4s.zio.http.SimpleRestJsonBuilder
import smithy4s.{Service, ShapeId}
import zio.{Task, ZIO}

import java.nio.file.Path

object ZIOHttpRestJsonProtocolTests extends ProtocolComplianceSuite {

  // in particular around floating-point precision.
  private val jsDisallowed = Set(
    "RestJsonInputWithHeadersAndAllParams",
    "RestJsonHttpRequestLabelEscaping"
  )

  override def allRules(
      dsi: DynamicSchemaIndex
  ): Task[compliancetests.ComplianceTest[Task] => compliancetests.ShouldRun] = {
    // Decoding borrowed tests
    ZIO
      .fromEither(
        smithy4s.Document
          .DObject(dsi.metadata)
          .decode[AlloyBorrowedTests]
      )
      .map { borrowedTests =>
        borrowedTests.simpleRestJsonBorrowedTests
          .getOrElse(ShapeId("aws.protocols", "restJson1"), AllowRules.empty)
      }
      .map { decodedRules => (c: compliancetests.ComplianceTest[Task]) =>
        if (c.show.contains("alloy")) ShouldRun.Yes
        else decodedRules.shouldRun(c)
      }

  }

  override def allTests(
      dsi: DynamicSchemaIndex
  ): List[compliancetests.ComplianceTest[Task]] =
    genServerTests(
      SimpleRestJsonIntegration,
      simpleRestJsonSpec,
      pizzaSpec
    )(dsi)
  private val modelDump: Task[Path] = fileFromEnv("MODEL_DUMP")

  override def dynamicSchemaIndexLoader: Task[DynamicSchemaIndex] = {
    for {
      p <- modelDump
      dsi <- ZIO
        .readFile(p)
        .map(_.getBytes)
        .map(decodeDocument(_, smithy4s.json.Json.payloadDecoders))
        .flatMap(e => ZIO.fromEither(loadDynamic(e)))
    } yield dsi
  }

  private val simpleRestJsonSpec =
    ShapeId("aws.protocoltests.restjson", "RestJson")

  private val pizzaSpec = ShapeId("alloy.test", "PizzaAdminService")
  object SimpleRestJsonIntegration extends Router {

    type Protocol = SimpleRestJson
    val protocolTag = alloy.SimpleRestJson

    override def routes[Alg[_[_, _, _, _, _]]](impl: FunctorAlgebra[Alg, Task])(
        implicit service: Service[Alg]
    ): Task[HttpRoutes] =
      SimpleRestJsonBuilder(service).routes(impl).lift

    def expectedResponseType(schema: Schema[?]): HttpMediaType = HttpMediaType(
      "application/json"
    )
    /*    override def reverseRoutes[Alg[_[_, _, _, _, _]]](routes: HttpApp[Any], testHost: Option[String])(implicit service: Service[Alg]): Task[FunctorAlgebra[Alg, Task]] = {

    val baseUri = URL.decode("http://localhost").toOption.get
    val suppliedHost =
      testHost.flatMap(host => URL.decode(s"http://$host").toOption)
    SimpleRestJsonBuilder(service)
      .client(routes.)
      .uri(suppliedHost.getOrElse(baseUri))
  }*/
  }

}
