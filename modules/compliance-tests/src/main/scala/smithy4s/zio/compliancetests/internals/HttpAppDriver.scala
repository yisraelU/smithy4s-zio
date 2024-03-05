package smithy4s.zio.compliancetests.internals

import zio.*
import zio.http.ZClient.Driver
import zio.http.{
  Body,
  ClientSSLConfig,
  Headers,
  HttpApp,
  Method,
  Request,
  Response,
  URL,
  Version,
  WebSocketApp
}

class HttpAppDriver(app: HttpApp[Any]) extends Driver[Any, Throwable] {
  override def request(
      version: Version,
      method: Method,
      url: URL,
      headers: Headers,
      body: Body,
      sslConfig: Option[ClientSSLConfig],
      proxy: Option[http.Proxy]
  )(implicit trace: Trace): ZIO[Any & Scope, Throwable, Response] = {
    app(Request(version, method, url, headers, body, None))
      .mapError(e => new RuntimeException(e.toString))

  }

  override def socket[Env1 <: Any](
      version: Version,
      url: URL,
      headers: Headers,
      app: WebSocketApp[Env1]
  )(implicit trace: Trace): ZIO[Env1 & Scope, Throwable, Response] = ZIO.never
}
