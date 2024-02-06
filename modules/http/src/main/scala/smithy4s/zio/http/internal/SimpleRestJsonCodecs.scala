package smithy4s.zio.http.internal

import smithy4s.Blob
import smithy4s.client.UnaryClientCodecs
import smithy4s.codecs.BlobEncoder
import smithy4s.http._
import smithy4s.json.Json
import smithy4s.zio.http.SimpleProtocolCodecs
import smithy4s.zio.http.internal.ZHttpToSmithy4sClient.ResourcefulTask
import zio.Scope
import zio.http.{Request, Response, URL}

// scalafmt: {maxColumn = 120}
private[http] class SimpleRestJsonCodecs(
    val maxArity: Int,
    val explicitDefaultsEncoding: Boolean,
    val hostPrefixInjection: Boolean
) extends SimpleProtocolCodecs {
  private val hintMask =
    alloy.SimpleRestJson.protocol.hintMask

  private val jsonCodecs = Json.payloadCodecs
    .withJsoniterCodecCompiler(
      Json.jsoniter
        .withHintMask(hintMask)
        .withMaxArity(maxArity)
        .withExplicitDefaultsEncoding(explicitDefaultsEncoding)
    )

  // val mediaType = HttpMediaType("application/json")
  private val payloadEncoders: BlobEncoder.Compiler =
    jsonCodecs.encoders

  private val payloadDecoders =
    jsonCodecs.decoders

  // Adding X-Amzn-Errortype as well to facilitate interop with Amazon-issued code-generators.
  private val errorHeaders = List(
    smithy4s.http.errorTypeHeader,
    smithy4s.http.amazonErrorTypeHeader
  )

  def makeClientCodecs(
      url: URL
  ): UnaryClientCodecs.Make[ResourcefulTask, Request, Response] = {
    implicit val monadThrowLike = zioMonadThrowLike[Scope]
    val baseRequest = HttpRequest(HttpMethod.POST, toSmithy4sHttpUri(url, None), Map.empty, Blob.empty)
    HttpUnaryClientCodecs.builder
      .withBodyEncoders(payloadEncoders)
      .withSuccessBodyDecoders(payloadDecoders)
      .withErrorBodyDecoders(payloadDecoders)
      .withErrorDiscriminator(HttpDiscriminator.fromResponse(errorHeaders, _).pure)
      .withMetadataDecoders(Metadata.Decoder)
      .withMetadataEncoders(Metadata.Encoder)
      .withBaseRequest(_ => baseRequest.pure)
      .withRequestMediaType("application/json")
      .withRequestTransformation(fromSmithy4sHttpRequest(_).pure)
      .withResponseTransformation[Response](toSmithy4sHttpResponse)
      .withHostPrefixInjection(hostPrefixInjection)
      .build()

  }

  def makeServerCodecs = {
    implicit val monadThrowLike = zioMonadThrowLike[Any]
    val baseResponse = HttpResponse(200, Map.empty, Blob.empty)

    HttpUnaryServerCodecs.builder
      .withBodyDecoders(payloadDecoders)
      .withSuccessBodyEncoders(payloadEncoders)
      .withErrorBodyEncoders(payloadEncoders)
      .withErrorTypeHeaders(errorHeaders: _*)
      .withMetadataDecoders(Metadata.Decoder)
      .withMetadataEncoders(Metadata.Encoder)
      .withBaseResponse(_ => baseResponse.pure)
      .withResponseMediaType("application/json")
      .withWriteEmptyStructs(!_.isUnit)
      .withRequestTransformation[Request](toSmithy4sHttpRequest)
      .withResponseTransformation(fromSmithy4sHttpResponse(_).pure)
      .build()
  }

}
