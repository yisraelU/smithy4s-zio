package smithy4s.zio.http

import alloy.SimpleRestJson
import smithy4s.Service
import smithy4s.example.WeatherGen
import smithy4s.zio.http.builders.client.ClientBuilder
import zio.http.Client
import zio.{Scope, ZEnvironment, ZIO, ZLayer}

object SimpleRestJsonBuilder extends SimpleRestJsonBuilder(1024, false, true)

class SimpleRestJsonBuilder private (
    simpleRestJsonCodecs: internal.SimpleRestJsonCodecs
) extends SimpleProtocolBuilder[alloy.SimpleRestJson](
      simpleRestJsonCodecs
    ) {

  def this(
      maxArity: Int,
      explicitDefaultsEncoding: Boolean,
      hostPrefixInjection: Boolean
  ) =
    this(
      new internal.SimpleRestJsonCodecs(
        maxArity,
        explicitDefaultsEncoding,
        hostPrefixInjection
      )
    )

  def withMaxArity(maxArity: Int): SimpleRestJsonBuilder =
    new SimpleRestJsonBuilder(
      maxArity,
      simpleRestJsonCodecs.explicitDefaultsEncoding,
      simpleRestJsonCodecs.hostPrefixInjection
    )

  def withExplicitDefaultsEncoding(
      explicitDefaultsEncoding: Boolean
  ): SimpleRestJsonBuilder =
    new SimpleRestJsonBuilder(
      simpleRestJsonCodecs.maxArity,
      explicitDefaultsEncoding,
      simpleRestJsonCodecs.hostPrefixInjection
    )

  def disableHostPrefixInjection(): SimpleRestJsonBuilder =
    new SimpleRestJsonBuilder(
      simpleRestJsonCodecs.maxArity,
      simpleRestJsonCodecs.explicitDefaultsEncoding,
      false
    )
}
