import examples.{Food, MenuItem, Pizza, PizzaAdminService, PizzaAdminServiceGen}
import smithy4s.zio.http.{ServiceOps, SimpleRestJsonBuilder}
import zhttp.http.URL
import zhttp.service.Client
import zio.{Scope, ZIO, ZIOAppArgs}

object MyApp extends zio.ZIOAppDefault {
  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = {

    val x = for {
      client <- Client.make[Any]
      clientService <- PizzaAdminService.simpleRestJson.client(
        URL.empty,
        client
      )
    } yield clientService

    x.map(_.addMenuItem("myfavoritePizza", null))

    ???
  }
}
