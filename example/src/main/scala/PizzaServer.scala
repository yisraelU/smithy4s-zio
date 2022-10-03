import zhttp.service.Server
import zhttp._
import zhttp.service.ServerChannelFactory
import zhttp.service.{EventLoopGroup, Server}
import zhttp.service.server.ServerChannelFactory
import zio._
import zio.ZIO.console
import zio.metrics.jvm.DefaultJvmMetrics.app

import scala.util.Try

object PizzaServer extends ZIOAppDefault {
  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = ???
}
