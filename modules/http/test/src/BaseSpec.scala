package smithy4s.disney.tests


import smithy4s.http.CaseInsensitive
import smithy4s.Monadic
import zhttp.http.URL
import zhttp.service.Client
import zio.Task


object BaseSpec {
  case class ClientWithInfo(client: Client[Any], uri: URL)
}

trait BaseSpec[Alg[_[_, _, _, _, _]]]
  extends IOSuite
{

  import BaseSpec._

  def runServer(
                 testService: Monadic[Alg, Task],
                 errorAdapter: PartialFunction[Throwable, Throwable]
               ): Resource[IO, ClientWithInfo]

  type Res = ClientWithInfo
  def sharedResource: Resource[IO, ClientWithInfo]

  def routerTest(testName: TestName)(
    f: (ClientWithInfo, Log[IO]) => IO[Expectations]
  ) = test(testName)((cli: ClientWithInfo, log: Log[IO]) => f(cli, log))

  implicit class ClientOps(client: Client[IO]) {
    // Returns: (status, headers, body)
    def send[A: Show](
                       request: Request[IO],
                       log: Log[IO]
                     )(implicit A: EntityDecoder[IO, A]): IO[(Int, HeaderMap, A)] =
      client.run(request).use { response =>
        val code = response.status.code
        val headers =
          HeaderMap {
            response.headers.headers
              .groupBy(ci => CaseInsensitive(ci.name.toString))
              .map { case (k, v) =>
                k -> v.map(_.value)
              }
          }
        val payloadIO = response.as[A]
        log.info("code = " + code) *>
          log.info("headers = " + headers) *>
          payloadIO.flatTap(p => log.info("payload = " + p.show)).map {
            payload => (code, headers, payload)
          }
      }

  }

  case class HeaderMap(
                        private val values: Map[CaseInsensitive, List[String]]
                      ) {
    def get(key: String): Option[List[String]] =
      values.get(CaseInsensitive(key))
  }

  implicit class JsonOps(json: Json) {
    def expect[A: Decoder](implicit loc: SourceLocation): IO[A] =
      json.as[A] match {
        case Left(value) =>
          IO.raiseError(AssertionException(value.message, NonEmptyList.of(loc)))
        case Right(value) => IO.pure(value)
      }
  }

}
