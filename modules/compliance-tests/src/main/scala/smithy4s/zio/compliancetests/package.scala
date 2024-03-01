package smithy4s.zio

import cats.{Eq, Monoid}
import smithy.test.HttpRequestTestCase
import smithy4s.zio.compliancetests.ComplianceTest.ComplianceResult
import smithy4s.zio.compliancetests.internals.asserts.*
import smithy4s.zio.compliancetests.internals.{asserts, parseQueryParams}
import cats.syntax.all.*
import smithy4s.zio.compliancetests.internals.asserts.testCase.{
  checkHeaders,
  checkQueryParameters
}
import zio.{Chunk, Promise, Scope, Task, ZIO, durationInt}
import zio.http.{
  Body,
  Header,
  Headers,
  MediaType,
  Method,
  QueryParams,
  Request,
  Routes,
  URL
}

import java.util.concurrent.TimeoutException
import zio.interop.catz.core.*

package object compliancetests {

  type HttpRoutes = Routes[Any, Throwable]
  type ResourcefulTask[Output] = ZIO[Scope, Throwable, Output]

  private[compliancetests] def makeRequest(
      baseUri: URL,
      testCase: HttpRequestTestCase
  ): Request = {
    val expectedHeaders = testCase.headers
      .map(headers => Headers.apply(headers.toList.map(Header.Custom.tupled)))
      .getOrElse(Headers.empty)

    val expectedContentType = testCase.bodyMediaType
      .flatMap(MediaType.forContentType)
      .map(contentType => Headers(Header.ContentType(contentType)))
      .getOrElse(Headers.empty)

    val allExpectedHeaders = expectedHeaders ++ expectedContentType

    val expectedMethod = Method
      .fromString(testCase.method)

    val expectedUrl: URL = constructUrl(baseUri, testCase)

    val body =
      testCase.body
        .map(Body.fromString(_))
        .getOrElse(Body.empty)

    Request(
      method = expectedMethod,
      url = expectedUrl,
      headers = allExpectedHeaders,
      body = body
    )
  }

  private def constructUrl(baseUri: URL, testCase: HttpRequestTestCase) = {
    val expectedUri = baseUri
      .addPath(testCase.uri)
      .addQueryParams(
        QueryParams(parseQueryParams(testCase.queryParams).toList.map {
          case (k, v) =>
            (k, Chunk.fromIterable(v))
        }: _*)
      )
    expectedUri
  }

  def matchRequest(
      request: Request,
      testCase: HttpRequestTestCase,
      baseUri: URL
  ): Task[ComplianceResult] = {

    val bodyAssert: ZIO[Any, Throwable, ComplianceResult] =
      request.body.asString.map { responseBody =>
        bodyEql(
          responseBody,
          testCase.body,
          testCase.bodyMediaType
        )
      }

    val resolvedHostPrefix =
      testCase.resolvedHost
        .zip(testCase.host)
        .map { case (resolved, host) => resolved.split(host)(0) }

    val resolvedHostAssert: List[ComplianceResult] =
      request.url.host
        .zip(resolvedHostPrefix)
        .map { case (a, b) =>
          contains(a, b, "resolved host test :")
        }
        .toList

    val receivedPathSegments =
      request.url.path.segments
    val expectedPathSegments =
      URL.decode(testCase.uri).toOption.get.path.segments

    val expectedUrl = constructUrl(baseUri, testCase)
    val pathAssert: ComplianceResult =
      eql(
        receivedPathSegments.toList,
        expectedPathSegments.toList,
        "path test :"
      )
    val queryAssert: ComplianceResult = checkQueryParameters(
      testCase,
      expectedUrl.queryParams.map
    )
    val methodAssert: ComplianceResult = eql(
      request.method.name.toLowerCase(),
      testCase.method.toLowerCase(),
      "method test :"
    )
    val ioAsserts = (resolvedHostAssert ++ List(
      checkHeaders(testCase, request.headers),
      pathAssert,
      queryAssert,
      methodAssert
    )).map(ZIO.succeed(_)) :+ bodyAssert
    ioAsserts.combineAll(cats.Applicative.monoid[Task, ComplianceResult])

  }

  val timeOutMessage =
    """|Timed-out while waiting for an input.
       |
       |This probably means that the Router implementation either failed to decode the request
       |or failed to route the decoded input to the correct service method.
       |""".stripMargin

  def runCompare[I: Eq](
      inputPromise: Promise[Nothing, I],
      testModel: I
  ): ZIO[Any, TimeoutException, ComplianceResult] = {
    inputPromise.await
      .timeoutFail(new TimeoutException())(1.second)
      .map { foundInput =>
        asserts.eql(foundInput, testModel)
      }
      .catchSome { case _: TimeoutException =>
        ZIO.succeed(asserts.fail(timeOutMessage))
      }
  }

  implicit val headerMonoid: Monoid[Headers] = new Monoid[Headers] {
    override def empty: Headers = Headers.empty
    override def combine(x: Headers, y: Headers): Headers = x ++ y
  }
}
