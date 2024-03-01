package smithy4s.zio.compliancetests.internals

import cats.Eq
import cats.implicits.{
  catsKernelStdCommutativeMonoidForMap,
  catsKernelStdMonoidForMap
}
import cats.kernel.Monoid
import cats.syntax.all.*
import io.circe.Json
import io.circe.parser.*
import smithy.test.{HttpRequestTestCase, HttpResponseTestCase}
import smithy4s.zio.compliancetests.ComplianceTest.*
import zio.http.{Headers, QueryParams}
import zio.test.{TestResult, assertTrue}

object asserts {

  // private implicit val eventsEq: Eq[XmlEvent] = Eq.fromUniversalEquals

  def success: TestResult = assertTrue(true)
  def fail(msg: String): TestResult = assertTrue(false).??(msg)

  private def isJson(bodyMediaType: Option[String]): Boolean =
    bodyMediaType.exists(_.equalsIgnoreCase("application/json"))

  private def isXml(bodyMediaType: Option[String]) =
    bodyMediaType.exists(_.equalsIgnoreCase("application/xml"))

  private def jsonEql(result: String, testCase: String): ComplianceResult = {
    (result.isEmpty, testCase.isEmpty) match {
      case (true, true) => assertTrue(true)
      case _ =>
        (parse(result), parse(testCase)) match {
          case (Right(a), Right(b)) if Eq[Json].eqv(a, b) => success
          case (Left(a), Left(b)) => fail(s"Both JSONs are invalid: $a, $b")
          case (Left(a), _) =>
            fail(s"Result JSON is invalid: $result \n Error $a ")
          case (_, Left(b)) =>
            fail(s"TestCase JSON is invalid: $testCase \n Error $b")
          case (Right(a), Right(b)) =>
            fail(s"JSONs are not equal: result json: $a \n testcase json:  $b")
        }

    }
  }

  def contains(
      result: String,
      expected: String,
      prefix: String = ""
  ): ComplianceResult = {
    assertTrue(result.contains(expected))
      .??(
        s"$prefix the result value: ${pprint.apply(result)} contains the expected TestCase value ${pprint
            .apply(expected)}."
      )

  }

  /*  private def xmlEql(
      result: String,
      testCase: String
  ): Task[ComplianceResult] = {
    val parseXml: String => Task[List[XmlEvent]] = in =>
      ZStream.from(in)
        .pipeThrough(events(false))
        .through(normalize)
        .flatMap {
          case x @ XmlEvent.XmlString(value, _) =>
            // TODO: This normalizes out newlines/spaces but sometimes we want to include these (when they are between a start and end tag)
            if (value.exists(c => !c.isWhitespace)) Stream(x) else Stream.empty
          case other => Stream(other)
        }
        .compile
        .toList

    for {
      r <- parseXml(result)
      t <- parseXml(testCase)
    } yield {
      if (r == t) {
        success
      } else {
        val report = s"""|------- result -------
                         |$result
                         |
                         |$r
                         |------ expected ------
                         |$testCase
                         |
                         |$t
                         |""".stripMargin
        fail(report)

      }
    }
  }*/

  def eql[A: Eq](
      result: A,
      testCase: A,
      prefix: String = ""
  ): ComplianceResult = {
    assertTrue(result === testCase).??(
      s"$prefix the result value: ${pprint.apply(result)} is equal to the expected TestCase value ${pprint
          .apply(testCase)}."
    )
  }

  def bodyEql(
      result: String,
      testCase: Option[String],
      bodyMediaType: Option[String]
  ): ComplianceResult = {
    if (testCase.isDefined)
      if (isJson(bodyMediaType)) {
        jsonEql(result, testCase.getOrElse(""))
      } else if (isXml(bodyMediaType)) {
        ???
        // xmlEql(result, testCase.getOrElse(""))
      } else {
        eql(result, testCase.getOrElse(""))
      }
    else success
  }

  private def queryParamsExistenceCheck(
      queryParameters: Map[String, Seq[String]],
      requiredParameters: Option[List[String]],
      forbiddenParameters: Option[List[String]]
  ): ComplianceResult = {
    val receivedParams = queryParameters.keySet
    val checkRequired = requiredParameters.foldMap { requiredParams =>
      requiredParams.map { param =>
        val errorMessage =
          s"Required query parameter $param was not present in the request"

        if (receivedParams.contains(param)) success
        else fail(errorMessage)
      }
    }.combineAll

    val checkForbidden: ComplianceResult = forbiddenParameters.foldMap {
      forbiddenParams =>
        forbiddenParams.map { param =>
          val errorMessage =
            s"Forbidden query parameter $param was present in the request"
          if (receivedParams.contains(param)) fail(errorMessage)
          else success
        }
    }.combineAll

    checkRequired |+| checkForbidden
  }

  private def queryParamValuesCheck(
      queryParameters: Map[String, Seq[String]],
      testCase: Option[List[String]]
  ): ComplianceResult = {
    val combined: Map[String, List[String]] = testCase.toList.flatten
      .map(QueryParams.decode(_))
      .map(_.map.map(t => (t._1, t._2.toList)))
      .combineAll

    val result = combined.foldLeft(List.empty[ComplianceResult]) {
      case (acc, (key, _)) if !queryParameters.contains(key) =>
        fail(s"missing query parameter $key") :: acc
      case (acc, (key, expectedValue)) =>
        val values = queryParameters.get(key).toList.flatten
        if (!(values == expectedValue)) {
          fail(s"query parameter $key has value ${pprint.apply(
              queryParameters.get(key).toList.flatten
            )} but expected ${pprint.apply(expectedValue)}") :: acc
        } else success :: acc
    }

    result.combineAll

  }

  /**
   * A list of header field names that must appear in the serialized HTTP message, but no assertion is made on the value.
   * Headers listed in headers do not need to appear in this list.
    */

  private def headersExistenceCheck(
      headers: Headers,
      requiredHeaders: Option[List[String]],
      forbiddenHeaders: Option[List[String]]
  ): ComplianceResult = {
    val checkRequired = requiredHeaders.toList.flatten.collect {
      case key if headers.get(key).isEmpty =>
        asserts.fail(s"Header $key is required request.")
    }.combineAll
    val checkForbidden = forbiddenHeaders.toList.flatten.collect {
      case key if headers.get(key).isDefined =>
        asserts.fail(s"Header $key is forbidden in the request.")
    }.combineAll
    checkRequired |+| checkForbidden
  }

  private def headerKeyValueCheck(
      headers: Map[CharSequence, CharSequence],
      expected: Option[Map[String, String]]
  ): ComplianceResult = {

    expected
      .map {
        _.toList
          .collect { case (key, value) =>
            headers.get(key) match {
              case Some(v) if v == value => success
              case Some(v) =>
                asserts.fail(
                  s"Header $key has value `$v` but expected `$value`"
                )
              case None =>
                fail(s"Header $key is missing in the request.")
            }
          }
          .combineAll
      }
      .getOrElse {
        success
      }

  }

  object testCase {

    def checkQueryParameters(
        tc: HttpRequestTestCase,
        queryParameters: Map[String, Seq[String]]
    ): ComplianceResult = {
      val existenceChecks = asserts.queryParamsExistenceCheck(
        queryParameters = queryParameters,
        requiredParameters = tc.requireQueryParams,
        forbiddenParameters = tc.forbidQueryParams
      )
      val valueChecks =
        asserts.queryParamValuesCheck(queryParameters, tc.queryParams)

      existenceChecks |+| valueChecks
    }

    def checkHeaders(
        tc: HttpRequestTestCase,
        headers: Headers
    ): ComplianceResult = {
      val existenceChecks = asserts.headersExistenceCheck(
        headers,
        requiredHeaders = tc.requireHeaders,
        forbiddenHeaders = tc.forbidHeaders
      )
      val valueChecks =
        asserts.headerKeyValueCheck(collapseHeaders(headers), tc.headers)
      existenceChecks |+| valueChecks
    }

    def checkHeaders(
        tc: HttpResponseTestCase,
        headers: Headers
    ): ComplianceResult = {
      val existenceChecks = asserts.headersExistenceCheck(
        headers,
        requiredHeaders = tc.requireHeaders,
        forbiddenHeaders = tc.forbidHeaders
      )
      val valueChecks =
        asserts.headerKeyValueCheck(collapseHeaders(headers), tc.headers)
      existenceChecks |+| valueChecks
    }
  }

  implicit val testResultMonoid: Monoid[TestResult] = new Monoid[TestResult] {
    override def empty: TestResult = success

    override def combine(x: TestResult, y: TestResult): TestResult = x && y
  }
}
