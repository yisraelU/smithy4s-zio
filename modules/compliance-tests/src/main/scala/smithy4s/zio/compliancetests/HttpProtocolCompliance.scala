

package smithy4s.zio.compliancetests

import smithy4s.Service
import zio.Task

/**
  * A construct allowing for running http protocol compliance tests against the implementation of a given protocol.
  *
  * Http protocol compliance tests are a bunch of Smithy traits provided by AWS to express expectations against
  * service definitions, making test specifications protocol-agnostic.
  *
  * See https://awslabs.github.io/smithy/2.0/additional-specs/http-protocol-compliance-tests.html?highlight=test
  */
object HttpProtocolCompliance {

  def clientTests[ Alg[_[_, _, _, _, _]]](
      reverseRouter: ReverseRouter[Task],
      service: Service[Alg]
  ): List[ComplianceTest[Task]] =
    new internals.ClientHttpComplianceTestCase[Task, Alg](
      reverseRouter,
      service
    ).allClientTests()

  def serverTests[ Alg[_[_, _, _, _, _]]](
      router: Router[Task],
      service: Service[Alg]
  ): List[ComplianceTest[Task]] =
    new internals.ServerHttpComplianceTestCase[Task, Alg](
      router,
      service
    ).allServerTests()

  def clientAndServerTests[ Alg[_[_, _, _, _, _]]](
                                                    router: Router[Task] & ReverseRouter[Task],
                                                    service: Service[Alg]
  ): List[ComplianceTest[Task]] =
    clientTests(router, service) ++ serverTests(router, service)

}
