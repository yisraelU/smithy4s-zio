package smithy4s.zio.compliancetests.internals

import cats.Eq
import cats.implicits.catsSyntaxSemigroup
import smithy.test.*
import smithy4s.codecs.PayloadError
import smithy4s.kinds.*
import smithy4s.zio.compliancetests.TestConfig.*
import smithy4s.zio.compliancetests.internals.eq.EqSchemaVisitor
import smithy4s.zio.compliancetests.{
  ComplianceTest,
  Router,
  makeRequest,
  runCompare
}
import smithy4s.{Document, Service}
import zio.http.{Response, URL}
import zio.interop.catz.concurrentInstance
import zio.{IO, Promise, Task, ZIO}
private[compliancetests] class ServerHttpComplianceTestCase[
    F[_],
    Alg[_[_, _, _, _, _]]
](
    router: Router,
    serviceInstance: Service[Alg]
) {

  import router.*

  private[compliancetests] val originalService: Service[Alg] = serviceInstance
  private val baseUri = URL.decode("http://localhost/").toOption.get

  private[compliancetests] def serverRequestTest[I, E, O, SE, SO](
      endpoint: originalService.Endpoint[I, E, O, SE, SO],
      testCase: HttpRequestTestCase
  ): ComplianceTest[Task] = {
    implicit val inputEq: Eq[I] = EqSchemaVisitor(endpoint.input)
    val testModel: IO[PayloadError, I] =
      ZIO.fromEither(
        CanonicalSmithyDecoder
          .fromSchema(endpoint.input)
          .decode(testCase.params.getOrElse(Document.obj()))
      )
    ComplianceTest[Task](
      testCase.id,
      testCase.protocol,
      endpoint.id,
      testCase.documentation,
      serverReq,
      run = Promise.make[Nothing, I].flatMap { inputPromise =>
        val fakeImpl: FunctorAlgebra[Alg, Task] =
          originalService.fromPolyFunction[Kind1[Task]#toKind5](
            new originalService.FunctorInterpreter[Task] {
              def apply[I_, E_, O_, SE_, SO_](
                  op: originalService.Operation[I_, E_, O_, SE_, SO_]
              ): Task[O_] = {
                val endpointInternal = originalService.endpoint(op)
                val in = originalService.input(op)
                if (endpointInternal.id == endpoint.id)
                  inputPromise.succeed(in.asInstanceOf[I]) *>
                    ZIO.die(new IntendedShortCircuit)
                else ZIO.die(new Throwable("Wrong endpoint called"))
              }
            }
          )

        routes(fakeImpl)(originalService)
          .flatMap { server =>
            // todo will change to run routes without sandboxing
            server.sandbox.toHttpApp
              .runZIO(makeRequest(baseUri, testCase))
              .flatMap((response: Response) => {
                if (response.status.isError)
                  // if we get a response we will run the test assertions
                  testModel.flatMap(runCompare(inputPromise, _))
                else
                  failWithBodyAsMessage(response)
              })
              .catchSomeDefect { case _: IntendedShortCircuit =>
                // run test here
                testModel.flatMap(runCompare(inputPromise, _))
              }

          }

      }
    )
  }

  private[compliancetests] def serverResponseTest[I, E, O, SE, SO](
      endpoint: originalService.Endpoint[I, E, O, SE, SO],
      testCase: HttpResponseTestCase,
      errorSchema: Option[ErrorResponseTest[_, E]] = None
  ): ComplianceTest[Task] = {

    ComplianceTest[Task](
      testCase.id,
      testCase.protocol,
      endpoint.id,
      testCase.documentation,
      serverRes,
      run = {
        val (amendedService, syntheticRequest) =
          prepareService(originalService, endpoint)

        val buildResult
            : Either[Document => Task[Throwable], Document => Task[O]] = {
          errorSchema
            .toLeft {
              val outputDecoder: Document.Decoder[O] =
                CanonicalSmithyDecoder.fromSchema(endpoint.output)
              (doc: Document) =>
                ZIO.fromEither(
                  outputDecoder
                    .decode(doc)
                )

            }
            .left
            .map(_.kleisliFy[Task])
        }

        val fakeImpl: FunctorInterpreter[NoInputOp, Task] =
          new FunctorInterpreter[NoInputOp, Task] {
            def apply[I_, E_, O_, SE_, SO_](
                op: NoInputOp[I_, E_, O_, SE_, SO_]
            ): Task[O_] = {
              val doc = testCase.params.getOrElse(Document.obj())
              buildResult match {
                case Left(onError) =>
                  onError(doc).flatMap { err =>
                    ZIO.fail(err)
                  }
                case Right(onOutput) =>
                  onOutput(doc).map(_.asInstanceOf[O_])
              }
            }
          }

        routes(fakeImpl)(amendedService)
          .flatMap { server =>
            server.sandbox.toHttpApp
              .runZIO(syntheticRequest)
              .flatMap { resp =>
                resp.body.asString
                  .map { body =>
                    (body, resp.status, resp.headers)
                  }
              }
              .map { case (actualBody, status, headers) =>
                assert
                  .bodyEql(actualBody, testCase.body, testCase.bodyMediaType)
                  .map { bodyAssert =>
                    bodyAssert |+|
                      assert.testCase.checkHeaders(testCase, headers) |+|
                      assert.eql(status.code, testCase.code)
                  }
              }
          }
      }
    )
  }

  def allServerTests(): List[ComplianceTest[Task]] = {
    def toResponse[I, E, O, SE, SO](
        endpoint: originalService.Endpoint[I, E, O, SE, SO]
    ): List[ComplianceTest[Task]] = {
      endpoint.error.toList
        .flatMap { errorschema =>
          errorschema.alternatives.flatMap { errorAlt =>
            errorAlt.schema.hints
              .get(HttpResponseTests)
              .toList
              .flatMap(_.value)
              .filter(_.protocol == protocolTag.id)
              .filter(tc => tc.appliesTo.forall(_ == AppliesTo.SERVER))
              .map(tc =>
                serverResponseTest(
                  endpoint,
                  tc,
                  errorSchema = Some(
                    ErrorResponseTest
                      .from(
                        errorAlt,
                        errorschema
                      )
                  )
                )
              )
          }
        }
    }

    originalService.endpoints.toList.flatMap { case endpoint =>
      val requestsTests = endpoint.hints
        .get(HttpRequestTests)
        .map(_.value)
        .getOrElse(Nil)
        .filter(_.protocol == protocolTag.id)
        .filter(tc => tc.appliesTo.forall(_ == AppliesTo.SERVER))
        .map(tc => serverRequestTest(endpoint, tc))

      val opResponseTests = endpoint.hints
        .get(HttpResponseTests)
        .map(_.value)
        .getOrElse(Nil)
        .filter(_.protocol == protocolTag.id)
        .filter(tc => tc.appliesTo.forall(_ == AppliesTo.SERVER))
        .map(tc => serverResponseTest(endpoint, tc))

      val errorResponseTests = toResponse(endpoint)
      requestsTests ++ opResponseTests ++ errorResponseTests
    }

  }

}
