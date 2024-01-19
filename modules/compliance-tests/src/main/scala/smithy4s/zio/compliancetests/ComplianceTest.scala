package smithy4s.zio.compliancetests

import smithy4s.ShapeId
import smithy4s.zio.compliancetests.ComplianceTest.ComplianceResult
import zio.test.TestResult

case class ComplianceTest[F[_]](
    id: String,
    protocol: ShapeId,
    endpoint: ShapeId,
    documentation: Option[String],
    config: TestConfig,
    run: F[ComplianceResult]
) {
  def show = s"${endpoint.id}${config.show}: $id"
}

object ComplianceTest {
  type ComplianceResult = TestResult
}
