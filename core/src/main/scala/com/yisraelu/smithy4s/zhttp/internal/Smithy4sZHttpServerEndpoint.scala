package com.yisraelu.smithy4s.zhttp.internal

import smithy4s.http._
import smithy4s._
import smithy4s.schema.Alt
import _root_.zio.{Chunk, IO, UIO, ZIO}
import com.yisraelu.smithy4s.zhttp.codecs.{EntityDecoder, EntityEncoder}
import com.yisraelu.smithy4s.zhttp.{
  getHeaders,
  toHeaders,
  toZhttpMethod,
  ZHttpResponse
}
import smithy4s.kinds.BiFunctorInterpreter
import zio.http._

/**
 * A construct that encapsulates a smithy4s endpoint, and exposes
 * http4s specific semantics.
 */

private[smithy4s] trait Smithy4sZHttpServerEndpoint[E] {
  def method: Method
  def matches(path: Array[String]): Option[PathParams]
  def run(pathParams: PathParams, request: Request): IO[E, Response]
  def matchPath(
      path: Array[String]
  ): Option[(Smithy4sZHttpServerEndpoint[E], PathParams)] =
    matches(path).map(this -> _)
}

private[smithy4s] object Smithy4sZHttpServerEndpoint {

  def apply[Op[_, _, _, _, _], I, E, O, SI, SO](
      impl: BiFunctorInterpreter[Op, IO],
      endpoint: Endpoint[Op, I, E, O, SI, SO],
      compilerContext: CompilerContext
  ): Either[HttpEndpoint.HttpEndpointError, Smithy4sZHttpServerEndpoint[E]] =
    HttpEndpoint.cast(endpoint).map { httpEndpoint =>
      new Smithy4sZHttpServerEndpointImpl[Op, I, E, O, SI, SO](
        impl,
        endpoint,
        httpEndpoint,
        compilerContext
      )
    }

}

private[smithy4s] class Smithy4sZHttpServerEndpointImpl[Op[
    _,
    _,
    _,
    _,
    _
], I, E, O, SI, SO](
    impl: BiFunctorInterpreter[Op, IO],
    endpoint: Endpoint[Op, I, E, O, SI, SO],
    httpEndpoint: HttpEndpoint[I],
    compilerContext: CompilerContext
) extends Smithy4sZHttpServerEndpoint[E] {

  import compilerContext._

  val method: Method = toZhttpMethod(httpEndpoint.method)

  def matches(path: Array[String]): Option[PathParams] = {
    httpEndpoint.matches(path)
  }

  def run(pathParams: PathParams, request: Request): IO[E, Response] = {
    val run: IO[E, O] = for {
      metadata <- getMetadata(pathParams, request)
      input <- extractInput(metadata, request)
      output <- impl(endpoint.wrap(input))
    } yield output

    run.foldZIO(e => errorResponse(e), o => successResponse(o))
  }

  private val inputSchema: Schema[I] = endpoint.input
  private val outputSchema: Schema[O] = endpoint.output

  private val inputMetadataDecoder =
    Metadata.PartialDecoder.fromSchema(inputSchema)
  private implicit val outputEntityEncoder: EntityEncoder[O] =
    entityCompiler.compileEntityEncoder(outputSchema, entityCache)
  private val outputMetadataEncoder =
    Metadata.Encoder.fromSchema(outputSchema)
  private implicit val httpContractErrorCodec
      : EntityEncoder[HttpContractError] =
    entityCompiler.compileEntityEncoder(HttpContractError.schema, entityCache)

  private val extractInput: (Metadata, Request) => IO[E, I] = {
    inputMetadataDecoder.total match {
      case Some(totalDecoder) =>
        (m, _) => ZIO.fromEither(totalDecoder.decode(m)).orDie

      case None =>
        // NB : only compiling the input codec if the data cannot be
        // totally extracted from the metadata.
        implicit val inputCodec: EntityDecoder[BodyPartial[I]] =
          entityCompiler.compilePartialEntityDecoder(inputSchema, entityCache)
        (metadata: Metadata, request: Request) =>
          for {
            metadataPartial <- ZIO
              .fromEither(
                inputMetadataDecoder.decode(metadata)
              )
              .orDie

            bodyPartial <- inputCodec
              .decode(request.headers, request.body)
              .orDie
            result <- ZIO
              .fromEither(metadataPartial.combineCatch(bodyPartial))
              .orDie
          } yield result

    }
  }

  private def putHeaders(m: Response, headers: Headers) =
    m.addHeaders(headers.headers)

  private def status(code: Int): Status = StatusParser
    .fromCode(code)
    .getOrElse(sys.error(s"Invalid status code: $code"))

  private def getMetadata(
      pathParams: PathParams,
      request: Request
  ): IO[E, Metadata] =
    ZIO.succeed(
      Metadata(
        path = pathParams,
        headers = getHeaders(request.headers),
        query = request.url.queryParams.map
          .collect { case (name, value) =>
            if (value.isEmpty) name -> List("true") else name -> value
          }
      )
    )

  private def successResponse(output: O): UIO[Response] = {
    val outputMetadata = outputMetadataEncoder.encode(output)
    val outputHeaders = toHeaders(outputMetadata.headers)

    ZIO.succeed(
      putHeaders(
        Response(
          status(outputMetadata.statusCode.getOrElse(httpEndpoint.code))
        ),
        outputHeaders
      )
        .withEntity(output)
    )
  }

  def compileErrorable(errorable: Errorable[E]): E => Response = {
    def errorHeaders(errorLabel: String, metadata: Metadata): Headers =
      toHeaders(metadata.headers).addHeader(errorTypeHeader, errorLabel)

    val errorUnionSchema = errorable.error
    val dispatcher =
      Alt.Dispatcher(errorUnionSchema.alternatives, errorUnionSchema.dispatch)
    type ErrorEncoder[Err] = Err => Response
    val precompiler = new Alt.Precompiler[Schema, ErrorEncoder] {
      def apply[Err](
          label: String,
          errorSchema: Schema[Err]
      ): ErrorEncoder[Err] = {
        implicit val errorCodec =
          entityCompiler.compileEntityEncoder(errorSchema, entityCache)
        val metadataEncoder = Metadata.Encoder.fromSchema(errorSchema)
        errorValue => {
          val errorCode =
            http.HttpStatusCode.fromSchema(errorSchema).code(errorValue, 500)
          val metadata = metadataEncoder.encode(errorValue)
          val headers = errorHeaders(label, metadata)
          val status =
            StatusParser
              .fromCode(errorCode)
              .getOrElse(Status.InternalServerError)
          Response(status, headers = headers).withEntity(errorValue)
        }
      }
    }
    dispatcher.compile(precompiler)
  }

  val errorResponse: E => IO[E, Response] = {
    endpoint.errorable match {
      case Some(errorable) =>
        val processError: E => Response = compileErrorable(errorable)
        (_: E) match {
          case e: HttpContractError =>
            ZIO.succeed(Response(Status.BadRequest).withEntity(e))
          case endpoint.Error((_, e)) => ZIO.succeed(processError(e))
          case e                      => ZIO.fail(e)
        }
      case None => (e: E) => ZIO.fail(e)
    }
  }

}
