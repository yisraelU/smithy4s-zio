package smithy4s.zio.http

import smithy4s.api.SimpleRestJson
import smithy4s.http.CodecAPI
import smithy4s.HintMask
import smithy4s.internals.InputOutput

object SimpleRestJsonBuilder
    extends SimpleProtocolBuilder[smithy4s.api.SimpleRestJson](
      smithy4s.http.json.codecs(
        smithy4s.api.SimpleRestJson.protocol.hintMask ++ HintMask(InputOutput)
      )
    )
