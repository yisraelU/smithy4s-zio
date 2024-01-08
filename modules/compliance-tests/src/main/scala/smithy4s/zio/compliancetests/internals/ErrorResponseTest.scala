package smithy4s.zio.compliancetests.internals

import cats.{ApplicativeThrow, Eq}
import cats.syntax.all._
import smithy4s.Document
import smithy4s.schema.{ErrorSchema, Schema}
import smithy4s.zio.compliancetests.ComplianceTest.ComplianceResult
import smithy4s.zio.compliancetests.internals.eq.EqSchemaVisitor

private[compliancetests] final case class ErrorResponseTest[A, E](
    schema: Schema[A],
    inject: A => E,
    dispatcher: E => Option[A],
    errorschema: ErrorSchema[E]
) {

  lazy val errorDecoder: Document.Decoder[A] =
    CanonicalSmithyDecoder.fromSchema(schema)
  implicit lazy val eq: Eq[A] = EqSchemaVisitor(schema)

  private def dispatchThrowable(t: Throwable): Option[A] = {
    errorschema.liftError(t).flatMap(dispatcher(_))
  }

  def errorEq[F[_]: ApplicativeThrow]
      : (Document, Throwable) => F[ComplianceResult] = {

    (doc: Document, throwable: Throwable) =>
      errorDecoder
        .decode(doc)
        .map(inject)
        .map { e =>
          (dispatcher(e), dispatchThrowable(throwable)) match {
            case (Some(expected), Some(result)) =>
              assert.eql(result, expected)
            case _ =>
              assert.fail(
                s"Could not decode error response to known model: $throwable"
              )
          }
        }
        .liftTo[F]
  }
  def kleisliFy[F[_]: ApplicativeThrow]: Document => F[Throwable] = {
    (doc: Document) =>
      errorDecoder
        .decode(doc)
        .map(inject)
        .map(errorschema.unliftError)
        .liftTo[F]
  }

}

private[compliancetests] object ErrorResponseTest {
  def from[E, A](
      errorAlt: smithy4s.schema.Alt[E, A],
      errorschema: smithy4s.schema.ErrorSchema[E]
  ): ErrorResponseTest[A, E] =
    ErrorResponseTest(
      errorAlt.schema,
      errorAlt.inject,
      errorAlt.project.lift,
      errorschema
    )
}
