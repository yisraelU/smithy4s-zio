/*package smithy4s.zio.http


import cats.Show
import cats.data.Chain
import cats.effect.std.UUIDGen
import cats.implicits.catsSyntaxOptionId
import org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher
import smithy4s.example.*
import smithy4s.Timestamp
import smithy4s.http.CaseInsensitive
import smithy4s.http.UnknownErrorResponse
import weaver.{Expectations, SourceLocation}
import weaver.Expectations.Helpers.{expect, failure}
import zio.http.codec.HttpCodec.Created
import zio.{Ref, Task, ZIO}
import zio.http.{Body, Client, Header, Headers, Request, Response, Status}
import zio.json.ast.Json
import zio.test.ZIOSpecDefault

abstract class PizzaClientSpec extends ZIOSpecDefault {

  val pizzaItem = Json.Obj(
    "pizza" -> Json.Obj(
      "name" -> Json.Str("margharita"),
      "base" -> Json.Str("T"),
      "toppings" -> Json.Arr(
        Json.Str("Mushroom"),
        Json.Str("Tomato")
      )
    )
  )

  clientTest("Errors make it through") { (client, backend) =>
    for {
      ts <- ZIO.succeed(Timestamp(Timestamp.nowUTC().epochSecond, 0))
      uuid <- ZIO.attempt(java.util.UUID.randomUUID())
      response <- Response(Status.Created,
        Headers(Header.Custom("X-ADDED-AT",
          ts.epochSecond.toString()
        )),
       Body.fromString(Json.Str(uuid.toString).toString())
      )
      _ <- backend.prepResponse("foo", response)
      menuItem = MenuItem(
        Food.PizzaCase(
          Pizza("margharita", PizzaBase.TOMATO, List(Ingredient.MUSHROOM))
        ),
        price = 9.5f
      )
      result <- client.addMenuItem("foo", menuItem)
      request <- backend.lastRequest("foo")
      requestBody <- request.body.asString
    } yield {

      val pizzaItem = Json.Obj(
        "pizza" -> Json.Obj(
          "name" -> Json.Str("margharita"),
          "base" -> Json.Str("T"),
          "toppings" -> Json.Arr(
            Json.Str("Mushroom")
          )
        )
      )

      val expectedBody = Json.Obj(
        "food" -> pizzaItem,
        "price" -> Json.Num(9.5f)
      )

      val expectedResult = AddMenuItemResult(uuid.toString(), ts)

      expect(requestBody == expectedBody) &&
        expect(request.url.path.toString == "/restaurant/foo/menu/item") &&
        expect(result == expectedResult)
    }
  }

  clientTestForError(
    "Receives errors as exceptions",
    Response(status = Status.NotFound)
      .addHeader(Header.Custom("X-Error-Type", "NotFoundError"))
      .copy(
        body = Body.fromString(Json.Obj("name" -> Json.Str("bar")).toString())
      ),
    NotFoundError("bar")
  )


  clientTestForError(
    "Handle error w/o a discriminator header nor a unique status code",
    Response(status = Status.ProxyAuthenticationRequired)
      .copy(
        body = Body.fromString(Json.Obj("message" -> Json.Str("generic client error message")).toString())
      ),
    unknownResponse(
      407,
      Map("Content-Length" -> "42", "Content-Type" -> "application/json"),
      """{"message":"generic client error message"}"""
    )
  )

  private def unknownResponse(
                               code: Int,
                               headers: Map[String, String],
                               body: String
                             ): UnknownErrorResponse =
    UnknownErrorResponse(
      code,
      headers.map { case (k, v) => CaseInsensitive(k) -> List(v) },
      body
    )

  clientTest("Headers are case insensitive") { (client, backend) =>
    for {
      res <- client.headerEndpoint(
        uppercaseHeader = "upper".some,
        capitalizedHeader = "capitalized".some,
        lowercaseHeader = "lowercase".some,
        mixedHeader = "mixed".some
      )
    } yield {
      expect(res.uppercaseHeader == "upper".some) &&
        expect(res.capitalizedHeader == "capitalized".some) &&
        expect(res.lowercaseHeader == "lowercase".some) &&
        expect(res.mixedHeader == "mixed".some)
    }
  }

  clientTest("code decoded in structure") { (client, backend) =>
    val code = 201
    for {
      response <- Created
      _ <- backend.prepResponse(s"customCode$code", response)
      res <- client.customCode(code)
    } yield {
      expect(res.code == Some(201))
    }
  }

  clientTest("Round trip") { (client, backend) =>
    for {
      res <- client.roundTrip(
        label = "l",
        query = "q".some,
        header = "h".some,
        body = "b".some
      )
    } yield {
      val expected = RoundTripData(
        "l",
        header = Some("h"),
        query = Some("q"),
        body = Some("b")
      )
      expect.same(res, expected)
    }
  }

  def clientTestForError[E](
                             name: String,
                             response: Response,
                             expected: E
                           )(implicit
                             loc: SourceLocation,
                             ct: scala.reflect.ClassTag[E],
                             show: Show[E] = Show.fromToString[E]
                           ) = {
    clientTest(name) { (client, backend) =>
      for {
        _ <- backend.prepResponse(
          name,
          response
        )
        maybeResult <- client.getMenu(name).either
      } yield maybeResult match {
        case Right(_) => failure("expected failure")
        case Left(error: E) =>
          expect.same[E](error, expected)
        case Left(error) =>
          failure(s"Error of unexpected type: $error")

      }
    }
  }

  def clientTest(name: String)(
    f: (
      PizzaAdminService[Task],
        Backend,
      ) => Task[Expectations]
  ): Unit =
    test(name) { (res) => f(res._1, res._2) }

  // If right, TCP will be exercised.
  def makeClient: Either[
    Client => Task[PizzaAdminService[Task]],
    Int => Task[PizzaAdminService[Task]]
  ]



  case class Backend(ref: Ref[State]) {
    def prepResponse(key: String, response: Response): Task[Unit] =
      ref.update(_.prepResponse(key, response))

    def lastRequest(key: String): Task[Request] =
      ref.get.flatMap(_.lastRequest(key))
  }

  case class State(
                    requests: Map[String, Chain[Request]],
                    nextResponses: Map[String, Response]
                  ) {
    def lastRequest(key: String): Task[Request] =
      ZIO.fromOption(requests
      .get(key)
      .flatMap(_.lastOption)).orElseFail(new Throwable(s"Found no request matching $key"))

    def saveRequest(key: String, request: Request) = {
      val reqForKey = requests.getOrElse(key, Chain.empty)
      val updated = reqForKey.append(request)
      this.copy(requests = requests + (key -> updated))
    }

    def prepResponse(key: String, response: Response) =
      this.copy(nextResponses = nextResponses + (key -> response))

    def getResponse(key: String): Task[Response] =
      ZIO.fromOption(nextResponses.get(key)).orElseFail(new Throwable(s"Found no response matching $key"))
  }

  object State {
    val empty = State(Map.empty, Map.empty)
  }

  def router(ref: Ref[State]) = {
    def storeAndReturn(key: String, request: Request): Task[Response] = {
      // Collecting the whole body eagerly to make sure we don't consume it after closing the connection
      request.body.asChunk.flatMap { body =>
        ref
          .updateAndGet(
            _.saveRequest(key, request.copy(body = Body.fromChunk(body) ))
          )
          .flatMap(_.getResponse(key))
      }

    }

    object Q extends OptionalQueryParamDecoderMatcher[String]("query")

    HttpRoutes
      .of[Task] {
        case request @ (POST -> Root / "restaurant" / key / "menu" / "item") =>
          storeAndReturn(key, request)
        case request @ (GET -> Root / "restaurant" / key / "menu") =>
          storeAndReturn(key, request)
        case request @ POST -> Root / "headers" =>
          Ok().map(_.withHeaders(request.headers))
        case request @ POST -> Root / "roundTrip" / label :? Q(q) =>
          val headers = request.headers
            .get(CIString("HEADER"))
            .map(Headers(_))
            .getOrElse(Headers.empty)

          request.asJson
            .map(
              _.deepMerge(
                Json
                  .Obj(
                    "label" -> Json.Str(label),
                    "query" -> q.map(Json.Str).getOrElse(Json.Null)
                  )
                  .deepDropNullValues
              )
            )
            .flatMap(json => Ok(json, headers = headers))
        case request @ (GET -> Root / "custom-code" / IntVar(code)) =>
          storeAndReturn(s"customCode$code", request)

        case POST -> Root / "book" / _ =>
          val body = Json.Obj("message" -> Json.Str("test"))
          Ok(body)
      }
      .orNotFound
  }

  val randomInt = Resource.eval(Task(scala.util.Random.nextInt(9999)))

  val randomPort = randomInt.map(_ + 50000)

  def server(app: HttpApp[Task]): Resource[Task, Int]

  def retryResource[A](
                        resource: Resource[Task, A],
                        max: Int = 10
                      ): Resource[Task, A] =
    if (max <= 0) resource
    else resource.orElse(retryResource(resource, max - 1))

}

 */
