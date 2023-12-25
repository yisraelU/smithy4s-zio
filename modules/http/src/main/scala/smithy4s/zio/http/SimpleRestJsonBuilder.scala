package smithy4s.zio.http

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
