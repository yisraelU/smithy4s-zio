package smithy4s.zio.http.internal

import zio.http.Status

object StatusParser {

  val MinCode = 100
  val MaxCode = 599

  def fromCode(code: Int): Option[Status] = {

    if (code < MinCode || code > MaxCode) {
      None
    } else {

      val status = code match {
        case 100 => Status.Continue
        case 101 => Status.SwitchingProtocols
        case 102 => Status.Processing
        case 200 => Status.Ok
        case 201 => Status.Created
        case 202 => Status.Accepted
        case 203 => Status.NonAuthoritativeInformation
        case 204 => Status.NoContent
        case 205 => Status.ResetContent
        case 206 => Status.PartialContent
        case 207 => Status.MultiStatus
        case 300 => Status.MultipleChoices
        case 301 => Status.MovedPermanently
        case 302 => Status.Found
        case 303 => Status.SeeOther
        case 304 => Status.NotModified
        case 305 => Status.UseProxy
        case 307 => Status.TemporaryRedirect
        case 308 => Status.PermanentRedirect
        case 400 => Status.BadRequest
        case 401 => Status.Unauthorized
        case 402 => Status.PaymentRequired
        case 403 => Status.Forbidden
        case 404 => Status.NotFound
        case 405 => Status.MethodNotAllowed
        case 406 => Status.NotAcceptable
        case 407 => Status.ProxyAuthenticationRequired
        case 408 => Status.RequestTimeout
        case 409 => Status.Conflict
        case 410 => Status.Gone
        case 411 => Status.LengthRequired
        case 412 => Status.PreconditionFailed
        case 413 => Status.RequestEntityTooLarge
        case 414 => Status.RequestUriTooLong
        case 415 => Status.UnsupportedMediaType
        case 416 => Status.RequestedRangeNotSatisfiable
        case 417 => Status.ExpectationFailed
        case 421 => Status.MisdirectedRequest
        case 422 => Status.UnprocessableEntity
        case 423 => Status.Locked
        case 424 => Status.FailedDependency
        case 425 => Status.UnorderedCollection
        case 426 => Status.UpgradeRequired
        case 428 => Status.PreconditionRequired
        case 429 => Status.TooManyRequests
        case 431 => Status.RequestHeaderFieldsTooLarge
        case 500 => Status.InternalServerError
        case 501 => Status.NotImplemented
        case 502 => Status.BadGateway
        case 503 => Status.ServiceUnavailable
        case 504 => Status.GatewayTimeout
        case 505 => Status.HttpVersionNotSupported
        case 506 => Status.VariantAlsoNegotiates
        case 507 => Status.InsufficientStorage
        case 510 => Status.NotExtended
        case 511 => Status.NetworkAuthenticationRequired
        case _   => Status.Custom(code)

      }
      Some(status)
    }
  }
}
