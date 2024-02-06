package smithy4s.zio.compliancetests

import cats.implicits.toFoldableOps
import smithy4s.{Hints, Service, ShapeId}
import smithy4s.schema.Schema
import smithy4s.zio.compliancetests.ComplianceTest.ComplianceResult
import zio.ZIO
import zio.http.{Headers, Request, Response}

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import scala.collection.immutable.ListMap

package object internals {

  private val urlDecoder: String => String =
    URLDecoder.decode(_, StandardCharsets.UTF_8.toString)

  def prepareService[I, E, O, SE, SO, Alg[_[_, _, _, _, _]]](
      originalService: Service[Alg],
      endpoint: Service[Alg]#Endpoint[I, E, O, SE, SO]
  ): (Service.Reflective[NoInputOp], Request) = {
    val newHints = {
      val newHttp = smithy.api.Http(
        method = smithy.api.NonEmptyString("GET"),
        uri = smithy.api.NonEmptyString("/")
      )
      val code =
        endpoint.hints.get[smithy.api.Http].map(_.code).getOrElse(newHttp.code)
      Hints(newHttp.copy(code = code))
    }
    val amendedOperation =
      Schema
        .operation(ShapeId("custom", "endpoint"))
        .withHints(newHints)
        .withOutput(endpoint.output)
        .withErrorOption(endpoint.error)

    val amendedEndpoint =
      smithy4s.Endpoint[NoInputOp, Unit, E, O, Nothing, Nothing](
        amendedOperation,
        (_: Unit) => NoInputOp()
      )
    val request = Request.get("/")
    val amendedService =
      // format: off
      new Service.Reflective[NoInputOp] {
        override def id: ShapeId = ShapeId("custom", "service")
        override def endpoints: Vector[Endpoint[_, _, _, _, _]] = Vector(amendedEndpoint)
        override def input[I_, E_, O_, SI_, SO_](op: NoInputOp[I_, E_, O_, SI_, SO_]) : I_ = ???
        override def ordinal[I_, E_, O_, SI_, SO_](op: NoInputOp[I_, E_, O_, SI_, SO_]): Int = ???
        override def version: String = originalService.version
        override def hints: Hints = originalService.hints
      }
    // format: on
    (amendedService, request)
  }

  def failWithBodyAsMessage(
      response: Response
  ): ZIO[Any, Throwable, ComplianceResult] = {
    response.body.asString.map(message =>
      asserts.fail(
        s"Expected either an IntendedShortCircuit error or a 5xx response, but got a response with status ${response.status} and message ${message}"
      )
    )
  }
  private[compliancetests] def splitQuery(
      queryString: String
  ): (String, String) = {
    queryString.split("=", 2) match {
      case Array(k, v) =>
        (
          k,
          urlDecoder(v)
        )
      case Array(k) => (k, "")
      case _        => throw new IllegalArgumentException("Invalid query")
    }
  }

  private[compliancetests] def parseQueryParams(
      queryParams: Option[List[String]]
  ): ListMap[String, List[String]] = {
    queryParams.combineAll
      .map(splitQuery)
      .foldLeft[ListMap[String, List[String]]](ListMap.empty) {
        case (acc, (k, v)) =>
          acc.get(k) match {
            case Some(value) => acc + (k -> (value :+ v))
            case None        => acc + (k -> List(v))
          }
      }
  }

  private def escape(cs: CharSequence): String = {
    val str = cs.toString
    val withEscapedQuotes = str.replace("\"", "\\\"")
    if (str.contains(",")) {
      "\"" + withEscapedQuotes + "\""
    } else withEscapedQuotes
  }

  /**
   * If there's a single value for a given key, injects in the map without changes.
   * If there a multiple values for a given key, escape each value, escape quotes, then add quotes.
   */
  private[compliancetests] def collapseHeaders(
      headers: Headers
  ): Map[CharSequence, CharSequence] = {
    def append(
        acc: Map[CharSequence, List[CharSequence]],
        key: CharSequence,
        newValue: CharSequence
    ) = {

      (key -> acc
        .get(key)
        .map(existing => existing :+ newValue)
        .getOrElse(List(newValue)))
    }

    val multimap =
      headers.headers.iterator.foldLeft(
        Map.empty[CharSequence, List[CharSequence]]
      ) { case (acc, header) =>
        acc + append(acc, header.headerName, header.renderedValue)
      }
    multimap.collect {
      case (key, value :: Nil) => (key, value)
      case (key, values) if values.size > 1 =>
        (key, values.map(escape).mkString(", "))
    }

  }

}
