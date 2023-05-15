import examples.{AddMenuItemResult, MenuItem, PizzaAdminService}
import smithy4s.{Timestamp, UnsupportedProtocolError}
import smithy4s.zio.http.{ServiceOps, SimpleRestJsonBuilder}
import zhttp.service.Server
import zhttp._
import zhttp.service.ServerChannelFactory
import zhttp.service.{EventLoopGroup, Server}
import zhttp.service.server.ServerChannelFactory
import zio._
import zio.ZIO.console
import zio.metrics.jvm.DefaultJvmMetrics.app

import java.util.UUID
import scala.util.Try
import zhttp.service._
object PizzaServer extends ZIOAppDefault {
  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = ???

  private val server: ZIO[Any, UnsupportedProtocolError, Server[Any, Nothing]] =for {app <- SimpleRestJsonBuilder(PizzaAdminService).routes(PizzaImpl)
    .make.map(Server.app(_).withBinding("0.0.0.0", 8080).start
    .provideEnvironment(EventLoopGroup.auto() ++ ChannelFactory.auto))

                                                                                     } yield Server.port(9000) ++ Server.app( app)
  server.mapError()
  server.flatMap{
    s => s.make}

      .use(start =>
        console.putStrLn(s"Server started on port ${start.port}")
   *> ZIO.never,
      ).provideCustomLayer(ServerChannelFactory.auto ++ EventLoopGroup.auto(2))
      .exitCode
  }
}

object PizzaImpl extends PizzaAdminService[Task] {

  override def addMenuItem(
      restaurant: String,
      menuItem: MenuItem
  ): Task[AddMenuItemResult] = {

    val itemId = UUID.randomUUID().toString
    ZIO.succeed {
      AddMenuItemResult(itemId, Timestamp(10, 2))
    }
  }
}
