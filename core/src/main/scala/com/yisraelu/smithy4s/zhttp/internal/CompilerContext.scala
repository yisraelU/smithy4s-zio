package com.yisraelu.smithy4s.zhttp.internal

import com.yisraelu.smithy4s.zhttp.EntityCompiler
import com.yunion.smithy4s.zhttp.EntityCompiler
import smithy4s.http.Metadata

trait CompilerContext {
  val entityCompiler: EntityCompiler
  val entityCache: entityCompiler.Cache
  val metadataDecoderCache: Metadata.PartialDecoder.Cache
  val metadataEncoderCache: Metadata.PartialDecoder.Cache
}

object CompilerContext {

  def make(ec: EntityCompiler): CompilerContext =
    new CompilerContext {
      val entityCompiler: EntityCompiler = ec
      val entityCache: entityCompiler.Cache = entityCompiler.createCache()
      val metadataDecoderCache: Metadata.PartialDecoder.Cache =
        Metadata.PartialDecoder.createCache()
      val metadataEncoderCache: Metadata.PartialDecoder.Cache =
        Metadata.PartialDecoder.createCache()

    }

}
