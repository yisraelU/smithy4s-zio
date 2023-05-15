package com.yisraelu.smithy4s.zhttp.internal

import _root_.zio.{IO, UIO, ZIO}
import com.yisraelu.smithy4s.zhttp._
import com.yisraelu.smithy4s.zhttp.codecs._
import smithy4s._
import smithy4s.http._
import smithy4s.kinds.{Kind1, PolyFunction}
import smithy4s.schema.SchemaAlt
import zio.http._

private[zhttp] class Smithy4sZHttpClientEndpoint[I, E, O, SI, SO](
    baseUri: URL,
    client: Client,
    httpEndpoint: HttpEndpoint[I],
    endpoint: Endpoint.Base[I, E, O, SI, SO],
    compilerContext: CompilerContext
) extends (I => IO[E, O]) {

  import compilerContext._

  def apply(input: I): IO[E, O] = send(input)

  // All non typed errors are shifted to the unrecoverable silent channel via ZIO.die
  def send(input: I): IO[E, O] = {
    client
      .request(inputToRequest(input))
      .flatMapError(t => ZIO.die(t))
      .flatMap { response =>
        outputFromResponse(response)
      }
  }

  private val method: Method = toZhttpMethod(httpEndpoint.method)

  private val inputSchema: Schema[I] = endpoint.input
  private val outputSchema: Schema[O] = endpoint.output

  private val inputMetadataEncoder: Metadata.Encoder[I] =
    Metadata.Encoder.fromSchema(inputSchema)
  private val inputHasBody: Boolean =
    Metadata.TotalDecoder.fromSchema(inputSchema).isEmpty
  private implicit val inputEntityEncoder: EntityEncoder[I] =
    entityCompiler.compileEntityEncoder(inputSchema, entityCache)
  private val outputMetadataDecoder: Metadata.PartialDecoder[O] =
    Metadata.PartialDecoder.fromSchema(outputSchema)
  private implicit val outputCodec: EntityDecoder[BodyPartial[O]] =
    entityCompiler.compilePartialEntityDecoder(outputSchema, entityCache)

  def inputToRequest(input: I): Request = {
    val metadata: Metadata = inputMetadataEncoder.encode(input)
    val path: List[String] = httpEndpoint.path(input)
    val queryParams: Map[String, List[String]] = metadata.query.map {
      case (k, v) =>
        k -> v.toList
    }
    val host = baseUri.host
      .map {
        case str if !str.endsWith("/") => str.concat("/")
      }
      .getOrElse("")
    val url =
      baseUri.withPath(host + path.mkString("/")).withQueryParams(queryParams)

    val headers = toHeaders(metadata.headers)
    val body: Body = if (inputHasBody) {
      inputEntityEncoder.encode(input)._2
    } else {
      Body.empty
    }
    Request(body, headers, method, url, Version.`HTTP/1.1`, None)
  }

  private def outputFromResponse(response: Response): IO[E, O] =
    if (response.status.isSuccess) outputFromSuccessResponse(response)
    else outputFromErrorResponse(response).flatMap(e => ZIO.fail(e))

  private def outputFromSuccessResponse(response: Response): IO[E, O] = {
    val decoder = decodeResponse(outputMetadataDecoder)
    decoder(response)
  }

  private def errorResponseFallBack(response: Response): UIO[E] = {
    val headers: Map[CaseInsensitive, List[String]] =
      response.headers.toList.map { case header =>
        smithy4s.http.CaseInsensitive(header.headerName) -> List(
          header.renderedValue
        )
      }.toMap

    val code = response.status.code
    val resp: ZIO[Any, Throwable, Nothing] =
      response.body.asString.flatMap(body =>
        ZIO.fail(UnknownErrorResponse(code, headers, body))
      )
    resp
  }.orDie

  private def getErrorDiscriminator(
      response: Response
  ): ErrorAltPicker.ErrorDiscriminator = {
    getFirstHeader(response.headers, errorTypeHeader)
      .map(errorType =>
        ShapeId
          .parse(errorType)
          .map(shapeId => ErrorAltPicker.ErrorDiscriminator.FullId(shapeId))
          .getOrElse(ErrorAltPicker.ErrorDiscriminator.NameOnly(errorType))
      )
      .getOrElse(
        ErrorAltPicker.ErrorDiscriminator.StatusCode(response.status.code)
      )
  }

  private val outputFromErrorResponse: Response => UIO[E] = {
    endpoint.errorable match {
      case None => errorResponseFallBack
      case Some(err) =>
        val allAlternatives = err.error.alternatives
        val picker = new ErrorAltPicker(allAlternatives)
        type ErrorDecoder[_] = Response => UIO[E]
        val decodeFunction = new PolyFunction[SchemaAlt[E, *], ErrorDecoder] {
          def apply[A](alt: SchemaAlt[E, A]): Response => UIO[E] = {
            val schema = alt.instance
            val errorMetadataDecoder: Metadata.PartialDecoder[A] =
              Metadata.PartialDecoder.fromSchema(schema)
            implicit val errorCodec: EntityDecoder[BodyPartial[A]] =
              entityCompiler.compilePartialEntityDecoder(schema, entityCache)

            (response: Response) => {
              val decoder = decodeResponse[A](errorMetadataDecoder)
              decoder(response)
                .map(e => alt.inject(e))
                .merge

            }
          }
        }.unsafeCacheBy(allAlternatives.map(Kind1.existential(_)), identity(_))

        (response: Response) => {
          val discriminator = getErrorDiscriminator(response)
          picker.getPreciseAlternative(discriminator) match {
            case None => errorResponseFallBack(response)
            case Some(alt) =>
              decodeFunction(alt)(response)

          }
        }
    }
  }

  private def decodeResponse[T](
      metadataDecoder: Metadata.PartialDecoder[T]
  )(implicit
      entityDecoder: EntityDecoder[BodyPartial[T]]
  ): Response => IO[E, T] = {
    metadataDecoder.total match {
      case Some(totalDecoder) =>
        (response: Response) => {
          val metadata: Metadata = extractMetadata(response)
          ZIO.fromEither(totalDecoder.decode(metadata)).orDie
        }
      case None =>
        (response: Response) =>
          {
            val metadata: Metadata = extractMetadata(response)
            for {
              metadataPartial <- ZIO.fromEither(
                metadataDecoder.decode(metadata)
              )
              bodyPartial <- entityDecoder
                .decode(response.headers, response.body)
              result <- ZIO.fromEither(
                metadataPartial.combineCatch(bodyPartial)
              )
            } yield result
          }.orDie
    }
  }

  private def extractMetadata(response: Response) = {
    val headers = getHeaders(response.headers)
    val metadata =
      Metadata(
        headers = headers,
        statusCode = Some(response.status.code)
      )
    metadata

  }

}
