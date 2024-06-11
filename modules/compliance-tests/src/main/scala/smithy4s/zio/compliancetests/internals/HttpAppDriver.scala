package smithy4s.zio.compliancetests.internals

import zio.*
import zio.http.ZClient.Driver
import zio.http.{Body, ClientSSLConfig, Headers, Method, Request, Response, Routes, URL, Version, WebSocketApp}

class HttpAppDriver(app: Routes[Any, Response]) extends Driver[Any, Throwable] {
  override def request(
      version: Version,
      method: Method,
      url: URL,
      headers: Headers,
      body: Body,
      sslConfig: Option[ClientSSLConfig],
      proxy: Option[http.Proxy]
  )(implicit trace: Trace): ZIO[Any, Throwable, Response] = {
    app(Request(version, method, url, headers, body, None))
      .mapError(e => new RuntimeException(e.toString))

  }

  override def socket[Env1 <: Any](
      version: Version,
      url: URL,
      headers: Headers,
      app: WebSocketApp[Env1]
  )(implicit trace: Trace): ZIO[Any, Throwable, Response] = ZIO.never
}
