package smithy4s.zio.http.internal

import zhttp.http.Status

object StatusParser {

  def fromCode(code: Int): Option[Status] = {
    ???
  }
  /*
    code match {
  case  100 => Some(Status.Continue)
  case 1  => Status.SwitchingProtocols
      case => Status.Processing
      case => Status.Ok
      case => Status.Created
      case => Status.Accepted
      case => Status.NonAuthoritiveInformation
      case => Status.NoContent
      case => Status.ResetContent
      case => Status.PartialContent
      case => Status.MultiStatus
      case => Status.MultipleChoices
      case => Status.MovedPermanently
      case => Status.Found
      case => Status.SeeOther
      case => Status.NotModified
      case => Status.UseProxy
      case => Status.TemporaryRedirect
      case => Status.PermanentRedirect
      case => Status.BadRequest
      case => Status.Unauthorized
      case => Status.PaymentRequired
      case => Status.Forbidden
      case => Status.NotFound
      case => Status.MethodNotAllowed
      case => Status.NotAcceptable
      case => Status.ProxyAuthenticationRequired
      case => Status.RequestTimeout
      case => Status.Conflict
      case => Status.Gone
      case => Status.LengthRequired
      case => Status.PreconditionFailed
      case => Status.RequestEntityTooLarge
      case => Status.RequestUriTooLong
      case => Status.UnsupportedMediaType
      case => Status.RequestedRangeNotSatisfiable
      case => Status.ExpectationFailed
      case => Status.MisdirectedRequest
      case => Status.UnprocessableEntity
      case => Status.Locked
      case => Status.FailedDependency
      case => Status.UnorderedCollection
      case => Status.UpgradeRequired
      case => Status.PreconditionRequired
      case => Status.TooManyRequests
      case => Status.RequestHeaderFieldsTooLarge
      case => Status.InternalServerError
      case => Status.NotImplemented
      case => Status.BadGateway
      case => Status.ServiceUnavailable
      case => Status.GatewayTimeout
      case => Status.HttpVersionNotSupported
      case => Status.VariantAlsoNegotiates
      case => Status.InsufficientStorage
      case => Status.NotExtended
      case => Status.NetworkAuthenticationRequired
      case => Status.Custom(code)

  }*/

}
