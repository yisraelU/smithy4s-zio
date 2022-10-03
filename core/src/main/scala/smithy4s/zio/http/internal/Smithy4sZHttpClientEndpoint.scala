package smithy4s.zio.http.internal

import smithy4s.{errorTypeHeader, Endpoint, Existential, PolyFunction, Schema, ShapeId}
import smithy4s.http.{BodyPartial, ErrorAltPicker, HttpEndpoint, Metadata, MetadataError, UnknownErrorResponse}
import smithy4s.schema.SchemaAlt
import smithy4s.zio.http.{getFirstHeader, getHeaders, toHeaders, toZhttpMethod, EntityCompiler}
import smithy4s.zio.http.codecs.{EntityDecoder, EntityEncoder}
import zhttp.http._
import zhttp.service.Client
import zhttp.service.Client.Config
import zio.{Task, ZIO}

private[smithy4s] trait Smithy4sZHttpClientEndpoint[Op[
    _,
    _,
    _,
    _,
    _
], I, E, O, SI, SO] {
  def send(input: I): Task[O]
}

private[smithy4s] object Smithy4sZHttpClientEndpoint {

  def apply[R,Op[_, _, _, _, _], I, E, O, SI, SO](
      baseUri: URL,
      client: Client[R],
      endpoint: Endpoint[Op, I, E, O, SI, SO],
      entityCompiler: EntityCompiler
  ): Option[Smithy4sZHttpClientEndpoint[Op, I, E, O, SI, SO]] =
    HttpEndpoint.cast(endpoint).map { httpEndpoint =>
      new Smithy4sZHttpClientEndpointImpl[R,Op, I, E, O, SI, SO](
        baseUri,
        client,
        endpoint,
        httpEndpoint,
        entityCompiler
      )
    }

}

private[smithy4s] class Smithy4sZHttpClientEndpointImpl[R,Op[
    _,
    _,
    _,
    _,
    _
], I, E, O, SI, SO](
    baseUri: URL,
    client: Client[R],
    endpoint: Endpoint[Op, I, E, O, SI, SO],
    httpEndpoint: HttpEndpoint[I],
    entityCompiler: EntityCompiler
) extends Smithy4sZHttpClientEndpoint[Op, I, E, O, SI, SO] {
  // format: on

  def send(input: I): Task[O] = {
    client
      .request(inputToRequest(input), Config.empty)
      .flatMap { response =>
        outputFromResponse(response)
      }
  }

  private val method: Method = toZhttpMethod(httpEndpoint.method)

  private val inputSchema: Schema[I] = endpoint.input
  private val outputSchema: Schema[O] = endpoint.output

  private val inputMetadataEncoder =
    Metadata.Encoder.fromSchema(inputSchema)
  private val inputHasBody =
    Metadata.TotalDecoder.fromSchema(inputSchema).isEmpty
  private implicit val inputEntityEncoder: EntityEncoder[I] =
    entityCompiler.compileEntityEncoder(inputSchema)
  private val outputMetadataDecoder =
    Metadata.PartialDecoder.fromSchema(outputSchema)
  private implicit val outputCodec =
    entityCompiler.compilePartialEntityDecoder(outputSchema)

  def inputToRequest(input: I): Request = {
    val metadata: Metadata = inputMetadataEncoder.encode(input)
    val path: List[String] = httpEndpoint.path(input)
    val queryParams: Map[String, List[String]] = metadata.query.map {
      case (k, v) =>
        k -> v.toList
    }
    val uri = baseUri.setPath(path.mkString("/")).setQueryParams(queryParams)

    val headers = toHeaders(metadata.headers)
    val body: Body = if (inputHasBody) {
      inputEntityEncoder.encode(input)._2
    } else {
      Body.empty
    }
    Request(Version.`HTTP/1.1`, method, uri, headers = headers, body)
  }

  private def outputFromResponse(response: Response): Task[O] =
    if (response.status.isSuccess) outputFromSuccessResponse(response)
    else outputFromErrorResponse(response)

  private def outputFromSuccessResponse(response: Response): Task[O] = {
    decodeResponse(response, outputMetadataDecoder)
  }

  private def errorResponseFallBack(response: Response): Task[O] = {
    val headers: Map[String, String] = response.headers.toList.toMap
    val code = response.status.code
    response.body.asString.flatMap(body =>
      ZIO.fail(UnknownErrorResponse(code, headers, body))
    )
  }

  private def getErrorDiscriminator(response: Response) = {
    getFirstHeader(response.headers, errorTypeHeader)
      .map(errorType =>
        ShapeId
          .parse(errorType)
          .map(ErrorAltPicker.ErrorDiscriminator.FullId)
          .getOrElse(ErrorAltPicker.ErrorDiscriminator.NameOnly(errorType))
      )
      .getOrElse(
        ErrorAltPicker.ErrorDiscriminator.StatusCode(response.status.code)
      )
  }

  private val outputFromErrorResponse: Response => Task[O] = {
    endpoint.errorable match {
      case None => errorResponseFallBack
      case Some(err) =>
        val allAlternatives = err.error.alternatives
        val picker = new ErrorAltPicker(allAlternatives)
        type ErrorDecoder[A] = Response => Task[E]
        val decodeFunction = new PolyFunction[SchemaAlt[E, *], ErrorDecoder] {
          def apply[A](alt: SchemaAlt[E, A]): Response => Task[E] = {
            val schema = alt.instance
            val errorMetadataDecoder =
              Metadata.PartialDecoder.fromSchema(schema)
            implicit val errorCodec: EntityDecoder[BodyPartial[A]] =
              entityCompiler.compilePartialEntityDecoder(schema)

            (response: Response) => {
              decodeResponse[A](response, errorMetadataDecoder)
                .map(alt.inject)
            }
          }
        }.unsafeCache(allAlternatives.map(Existential.wrap(_)))

        (response: Response) => {
          val discriminator = getErrorDiscriminator(response)
          picker.getPreciseAlternative(discriminator) match {
            case None => errorResponseFallBack(response)
            case Some(alt) =>
              decodeFunction(alt)(response)
                .map(err.unliftError)
                .flatMap(ZIO.fail(_))

          }
        }
    }
  }

  private def decodeResponse[T](
      response: Response,
      metadataDecoder: Metadata.PartialDecoder[T]
  )(implicit
      entityDecoder: EntityDecoder[BodyPartial[T]]
  ): Task[T] = {
    val headers = getHeaders(response.headers)
    val metadata =
      Metadata(headers = headers, statusCode = Some(response.status.code))
    metadataDecoder.total match {
      case Some(totalDecoder) =>
        ZIO.succeed(totalDecoder.decode(metadata)).absolve
      case None =>
        for {
          metadataPartial <- ZIO.fromEither(metadataDecoder.decode(metadata))
          bodyPartial <- entityDecoder
            .decode(response.headers, response.body)
        } yield metadataPartial.combine(bodyPartial)
    }
  }
}
