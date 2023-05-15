package com.yisraelu.smithy4s.zhttp

import com.yisraelu.smithy4s.zhttp.SimpleRestJsonBuilder.SimpleRestJsonBuilder
import smithy4s.http.json.{codecs => jsonCodecs}

object SimpleRestJsonBuilder extends SimpleRestJsonBuilder(1024) {
  def apply(maxArity: Int): SimpleRestJsonBuilder =
    new SimpleRestJsonBuilder(maxArity)

  class SimpleRestJsonBuilder(maxArity: Int)
      extends SimpleProtocolBuilder[alloy.SimpleRestJson](
        smithy4s.http.json.codecs(
          alloy.SimpleRestJson.protocol.hintMask ++
            jsonCodecs.defaultHintMask,
          maxArity
        )
      ) {
    def withMaxArity(maxArity: Int): SimpleRestJsonBuilder =
      new SimpleRestJsonBuilder(maxArity)

  }
}
