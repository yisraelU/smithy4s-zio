package smithy4s.zio.compliancetests.internals

import cats.Eq
import cats.effect.Async
import cats.syntax.all.*
import smithy.test.*
import smithy4s.http.HttpContractError
import smithy4s.kinds.FunctorAlgebra
import smithy4s.zio.compliancetests.TestConfig.*
import smithy4s.zio.compliancetests.internals.eq.EqSchemaVisitor
import smithy4s.zio.compliancetests.{
  ComplianceTest,
  ResourcefulTask,
  ReverseRouter,
  headerMonoid,
  matchRequest
}
import smithy4s.{Document, Service}
import zio.http.{Body, Header, Headers, HttpApp, Request, Response, Status, URL}
import zio.{Promise, Scope, Task, ZIO, durationInt}

import java.util.concurrent.TimeoutException

private[compliancetests] class ClientHttpComplianceTestCase[
    Alg[_[_, _, _, _, _]]
](reverseRouter: ReverseRouter, serviceInstance: Service[Alg])(implicit
    ce: Async[Task]
) {
  import reverseRouter._
  private val baseUri = URL.decode("http://localhost/").toOption.get
  private[compliancetests] implicit val service: Service[Alg] = serviceInstance

  private[compliancetests] def clientRequestTest[I, E, O, SE, SO](
      endpoint: service.Endpoint[I, E, O, SE, SO],
      testCase: HttpRequestTestCase
  ): ComplianceTest[Task] = {
    type R[I_, E_, O_, SE_, SO_] = ResourcefulTask[O_]
    val inputFromDocument = CanonicalSmithyDecoder.fromSchema(endpoint.input)
    ComplianceTest[Task](
      testCase.id,
      testCase.protocol,
      endpoint.id,
      testCase.documentation,
      clientReq,
      run = ce.defer {
        val input = inputFromDocument
          .decode(testCase.params.getOrElse(Document.obj()))
          .liftTo[Task]

        Promise.make[Nothing, Request].flatMap { requestDeferred =>
          val app: HttpApp[Any] = HttpApp.collectZIO { case req =>
            req.body.asChunk
              .map(chunk => req.copy(body = zio.http.Body.fromChunk(chunk)))
              .flatMap(requestDeferred.succeed(_))
              .mapBoth(Response.fromThrowable, _ => Response())
          }

          reverseRoutes[Alg](app, testCase.host).flatMap {
            client: FunctorAlgebra[Alg, ResourcefulTask] =>
              input
                .flatMap { in =>
                  // avoid blocking the test forever...
                  val request = requestDeferred.await.timeoutFail(
                    new TimeoutException("Request timed out")
                  )(1.second)
                  val output: Task[O] = service
                    .toPolyFunction[R](client)
                    .apply(endpoint.wrap(in))
                    .provide(Scope.default)
                  output.attemptNarrow[HttpContractError].productR(request)
                }
                .flatMap { req => matchRequest(req, testCase, baseUri) }
          }
        }
      }
    )
  }

  private[compliancetests] def clientResponseTest[I, E, O, SE, SO](
      endpoint: service.Endpoint[I, E, O, SE, SO],
      testCase: HttpResponseTestCase,
      errorSchema: Option[ErrorResponseTest[_, E]] = None
  ): ComplianceTest[Task] = {

    type R[I_, E_, O_, SE_, SO_] = ResourcefulTask[O_]

    val dummyInput = DefaultSchemaVisitor(endpoint.input)

    ComplianceTest[Task](
      testCase.id,
      testCase.protocol,
      endpoint.id,
      testCase.documentation,
      clientRes,
      run = {
        implicit val outputEq: Eq[O] =
          EqSchemaVisitor(endpoint.output)
        val buildResult = {
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
            .map(_.errorEq[Task])
        }
        val status = ZIO.fromOption(Status.fromInt(testCase.code)).orElseFail {
          new IllegalArgumentException(
            s"Invalid status code ${testCase.code}"
          )
        }

        status.flatMap { status =>
          val app = HttpApp.collectZIO { case req =>
            val body = testCase.body

            val headers = testCase.headers.map(_.toList).foldMap { headers =>
              Headers(headers.map { case (k, v) =>
                Header.Custom(k, v)
              })
            }

            req.body.asString
              .as(
                Response(
                  status = status,
                  body = body.map(Body.fromString(_)).getOrElse(Body.empty)
                )
                  .addHeaders(headers)
              )
              .mapBoth(Response.fromThrowable, identity)
          }

          reverseRoutes[Alg](app).flatMap { client =>
            val doc: Document = testCase.params.getOrElse(Document.obj())
            buildResult match {
              case Left(onError) =>
                val res: Task[O] = service
                  .toPolyFunction[R](client)
                  .apply(endpoint.wrap(dummyInput))
                  .provide(Scope.default)
                res
                  .as(asserts.success)
                  .recoverWith { case ex: Throwable => onError(doc, ex) }
              case Right(onOutput) =>
                onOutput(doc).flatMap { expectedOutput =>
                  val res: Task[O] = service
                    .toPolyFunction[R](client)
                    .apply(endpoint.wrap(dummyInput))
                    .provide(Scope.default)

                  res.map { output =>
                    asserts.eql(
                      output,
                      expectedOutput
                    )
                  }
                }
            }
          }

        }
      }
    )
  }

  def allClientTests(): List[ComplianceTest[Task]] = {
    def toResponse[I, E, O, SE, SO, A](
        endpoint: service.Endpoint[I, E, O, SE, SO]
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
              .map { tc =>
                clientResponseTest(
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
              }
          }
        }
    }
    service.endpoints.toList.flatMap { case endpoint =>
      val requestTests = endpoint.hints
        .get(HttpRequestTests)
        .map(_.value)
        .getOrElse(Nil)
        .filter(_.protocol == protocolTag.id)
        .filter(tc => tc.appliesTo.forall(_ == AppliesTo.CLIENT))
        .map(tc => clientRequestTest(endpoint, tc))

      val opResponseTests = endpoint.hints
        .get(HttpResponseTests)
        .map(_.value)
        .getOrElse(Nil)
        .filter(_.protocol == protocolTag.id)
        .filter(tc => tc.appliesTo.forall(_ == AppliesTo.CLIENT))
        .map(tc => clientResponseTest(endpoint, tc))

      val errorResponseTests = toResponse(endpoint)

      requestTests ++ opResponseTests ++ errorResponseTests
    }
  }
}
