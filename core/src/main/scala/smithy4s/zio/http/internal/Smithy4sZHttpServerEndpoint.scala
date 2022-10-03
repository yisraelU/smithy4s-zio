package smithy4s.zio.http.internal

/*
 *  Copyright 2021-2022 Disney Streaming
 *
 *  Licensed under the Tomorrow Open Source Technology License, Version 1.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     https://disneystreaming.github.io/TOST-1.0.txt
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import smithy4s.{errorTypeHeader, http, Endpoint, Interpreter, Schema}
import smithy4s.http._
import smithy4s.schema.Alt
import smithy4s.zio.http.{
  getHeaders,
  toHeaders,
  toZhttpMethod,
  EntityCompiler,
  ZHttpResponse
}
import smithy4s.zio.http.codecs.{EntityDecoder, EntityEncoder}
import zhttp.http._
import zio.{Task, ZIO}

/**
 * A construct that encapsulates a smithy4s endpoint, and exposes
 * http4s specific semantics.
 */
private[smithy4s] trait Smithy4sZHttpServerEndpoint {
  def method: Method
  def matches(path: Array[String]): Option[PathParams]
  def run(pathParams: PathParams, request: Request): Task[Response]
  def matchTap(
      path: Array[String]
  ): Option[(Smithy4sZHttpServerEndpoint, PathParams)] =
    matches(path).map(this -> _)
}

private[smithy4s] object Smithy4sZHttpServerEndpoint {

  def apply[Op[_, _, _, _, _], I, E, O, SI, SO](
      impl: Interpreter[Op, Task],
      endpoint: Endpoint[Op, I, E, O, SI, SO],
      codecs: EntityCompiler,
      errorTransformation: PartialFunction[Throwable, Throwable]
  ): Option[Smithy4sZHttpServerEndpoint] =
    HttpEndpoint.cast(endpoint).map { httpEndpoint =>
      new Smithy4sZHttpServerEndpointImpl[Op, I, E, O, SI, SO](
        impl,
        endpoint,
        httpEndpoint,
        codecs,
        errorTransformation
      )
    }

}

// format: off
private[smithy4s] class Smithy4sZHttpServerEndpointImpl[ Op[_, _, _, _, _], I, E, O, SI, SO](
                                                                                              impl: Interpreter[Op, Task],
                                                                                              endpoint: Endpoint[Op, I, E, O, SI, SO],
                                                                                                  httpEndpoint: HttpEndpoint[I],
                                                                                                  codecs: EntityCompiler,
                                                                                                  errorTransformation: PartialFunction[Throwable, Throwable]
                                                                                                ) extends Smithy4sZHttpServerEndpoint {
  // format: on

  val method: Method = toZhttpMethod(httpEndpoint.method)

  def matches(path: Array[String]): Option[PathParams] = {
    httpEndpoint.matches(path)
  }

  def run(pathParams: PathParams, request: Request): Task[Response] = {
    val run: Task[O] = for {
      metadata <- getMetadata(pathParams, request)
      input <- extractInput(metadata, request)
      output <- (impl(endpoint.wrap(input)): Task[O])
    } yield output

    run.catchSome(transformError).either.flatMap {
      case Left(error)   => errorResponse(error)
      case Right(output) => successResponse(output)
    }
  }

  private val inputSchema: Schema[I] = endpoint.input
  private val outputSchema: Schema[O] = endpoint.output

  private val inputMetadataDecoder =
    Metadata.PartialDecoder.fromSchema(inputSchema)
  private implicit val outputEntityEncoder: EntityEncoder[O] =
    codecs.compileEntityEncoder(outputSchema)
  private val outputMetadataEncoder =
    Metadata.Encoder.fromSchema(outputSchema)
  private implicit val httpContractErrorCodec
      : EntityEncoder[HttpContractError] =
    codecs.compileEntityEncoder(HttpContractError.schema)

  private val transformError: PartialFunction[Throwable, Task[O]] = {
    case e @ endpoint.Error(_, _) => ZIO.fail(e)
    case scala.util.control.NonFatal(other)
        if errorTransformation.isDefinedAt(other) =>
      ZIO.fail(errorTransformation(other))
  }



  // format: off
  private val extractInput: (Metadata, Request) => Task[I] = {
    inputMetadataDecoder.total match {
      case Some(totalDecoder) =>
        (m,_) =>   ZIO.fromEither(totalDecoder.decode(m))

      case None =>
        // NB : only compiling the input codec if the data cannot be
        // totally extracted from the metadata.
        implicit val inputCodec:EntityDecoder[BodyPartial[I]] = codecs.compilePartialEntityDecoder(inputSchema)
        (metadata:Metadata, request:Request) =>
          for {
            metadataPartial <- ZIO.fromEither( inputMetadataDecoder.decode(metadata))
            bodyPartial <- inputCodec.decode(request.headers,request.body)
          } yield metadataPartial.combine(bodyPartial)

    }
  }
  // format: on

  private def putHeaders(m: Response, headers: Headers) =
    m.addHeaders(headers.headers)

  private def status(code: Int): Status = StatusParser
    .fromCode(code)
    .getOrElse(sys.error(s"Invalid status code: $code"))

  private def getMetadata(pathParams: PathParams, request: Request) =
    ZIO.succeed(
      Metadata(
        path = pathParams,
        headers = getHeaders(request.headers),
        query = request.url.queryParams
          .collect {
            case (name, Nil)   => name -> List("true")
            case (name, value) => name -> value
          }
      )
    )

  private def successResponse(output: O): Task[Response] = {
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

  private def errorResponse(throwable: Throwable): Task[Response] = {

    def errorHeaders(errorLabel: String, metadata: Metadata): Headers =
      toHeaders(metadata.headers)
        .addHeader(errorTypeHeader, errorLabel)

    def processAlternative[ErrorUnion, ErrorType](
        altAndValue: Alt.SchemaAndValue[ErrorUnion, ErrorType]
    ): Response = {
      val errorSchema = altAndValue.alt.instance
      val errorValue = altAndValue.value
      val errorCode =
        http.HttpStatusCode.fromSchema(errorSchema).code(errorValue, 500)
      implicit val errorCodec = codecs.compileEntityEncoder(errorSchema)
      val metadataEncoder = Metadata.Encoder.fromSchema(errorSchema)
      val metadata = metadataEncoder.encode(errorValue)
      val headers = errorHeaders(altAndValue.alt.label, metadata)
      val status =
        StatusParser.fromCode(errorCode).getOrElse(Status.InternalServerError)
      Response(status, headers = headers).withEntity(errorValue)
    }

    ZIO
      .succeed(throwable)
      .flatMap {
        case e: HttpContractError =>
          ZIO.succeed(Response(Status.BadRequest).withEntity(e))
        case endpoint.Error((errorable, e)) =>
          ZIO.succeed(processAlternative(errorable.error.dispatch(e)))
        case e: Throwable =>
          ZIO.fail(e)
      }
  }

}
