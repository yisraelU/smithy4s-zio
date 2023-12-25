/*package smithy4s.zio.http

object Http4sPizzaClientSpec extends PizzaClientSpec {
  def makeClient: Either[
    HttpApp[IO] => Resource[IO, PizzaAdminService[IO]],
    Int => Resource[IO, PizzaAdminService[IO]]
  ] = Left { httpApp =>
    SimpleRestJsonBuilder(PizzaAdminService)
      .client(Client.fromHttpApp(httpApp))
      .resource
  }

}

 */
