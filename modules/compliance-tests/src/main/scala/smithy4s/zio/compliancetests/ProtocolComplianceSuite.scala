package smithy4s.zio.compliancetests

import smithy4s.codecs.*
import smithy4s.dynamic.DynamicSchemaIndex
import smithy4s.dynamic.DynamicSchemaIndex.load
import smithy4s.dynamic.model.Model
import smithy4s.{Blob, Document, Schema, ShapeId}
import zio.test.*
import zio.{Scope, Task, ZIO}

import java.nio.file.{Path, Paths}
import scala.jdk.CollectionConverters.MapHasAsScala

abstract class ProtocolComplianceSuite extends ZIOSpecDefault {

  def allRules(dsi: DynamicSchemaIndex): Task[ComplianceTest[Task] => ShouldRun]

  def allTests(dsi: DynamicSchemaIndex): List[ComplianceTest[Task]]

  override def spec: Spec[TestEnvironment & Scope, Any] = {
    suite("Protocol Compliance Tests") {
      makeTests()
    }
  }

  def makeTests(): ZIO[Any, Throwable, List[Spec[Any, Throwable]]] = {
    //  val includeTest = Filters.filterTests(this.)(getArgs)
    dynamicSchemaIndexLoader
      .flatMap(dsi => allRules(dsi).map(_ -> allTests(dsi)))
      .map { case (rules, tests) => tests.map((_, rules)) }
      /*    .flatMap { case (rules, test) =>
        if (includeTest(test.show)) ZStream.from((rules, test)) else ZStream.empty
      }*/
      .map { all =>
        all.map { case (test, rules) =>
          makeTest(rules, test)
        }
      }
  }

  def dynamicSchemaIndexLoader: Task[DynamicSchemaIndex]

  def genClientTests(
      impl: ReverseRouter,
      shapeIds: ShapeId*
  )(dsi: DynamicSchemaIndex): List[ComplianceTest[Task]] =
    shapeIds.toList.flatMap(shapeId =>
      dsi
        .getService(shapeId)
        .toList
        .flatMap(wrapper => {
          HttpProtocolCompliance
            .clientTests(
              impl,
              wrapper.service
            )
        })
    )

  def genServerTests(
      impl: Router,
      shapeIds: ShapeId*
  )(dsi: DynamicSchemaIndex): List[ComplianceTest[Task]] =
    shapeIds.toList.flatMap(shapeId =>
      dsi
        .getService(shapeId)
        .toList
        .flatMap(wrapper => {
          HttpProtocolCompliance
            .serverTests(
              impl,
              wrapper.service
            )
        })
    )

  def genClientAndServerTests(
      impl: ReverseRouter with Router,
      shapeIds: ShapeId*
  )(dsi: DynamicSchemaIndex): List[ComplianceTest[Task]] =
    shapeIds.toList.flatMap(shapeId =>
      dsi
        .getService(shapeId)
        .toList
        .flatMap(wrapper => {
          HttpProtocolCompliance
            .clientAndServerTests(
              impl,
              wrapper.service
            )
        })
    )

  def loadDynamic(
      doc: Document
  ): Either[PayloadError, DynamicSchemaIndex] = {
    Document.decode[Model](doc).map(load)
  }

  private[smithy4s] def fileFromEnv(key: String): Task[Path] = {

    ZIO
      .attempt(Option(System.getenv(key)))
      .flatMap(
        {
          ZIO
            .fromOption(_)
            .mapBoth(
              _ => sys.error("MODEL_DUMP env var not set"),
              Paths.get(_)
            )
        }
      )
  }

  def decodeDocument(
      bytes: Array[Byte],
      codecApi: BlobDecoder.Compiler
  ): Document = {
    val codec: PayloadDecoder[Document] = codecApi.fromSchema(Schema.document)
    codec
      .decode(Blob(bytes))
      .getOrElse(sys.error("unable to decode smithy model into document"))

  }

  private def makeTest(
      rule: ComplianceTest[Task] => ShouldRun,
      tc: ComplianceTest[Task]
  ): Spec[Any, Throwable] = {
    val shouldRun = rule(tc)
    shouldRun match {
      case ShouldRun.No => Spec.empty
      case ShouldRun.Yes =>
        test(tc.show)(tc.run)
      case ShouldRun.NotSure => Spec.empty
      /*   tc.run
            .map(assertion => unsureWhetherShouldSucceed(tc, assertion))*/
    }

  }

  /*  def unsureWhetherShouldSucceed(
                                  test: ComplianceTest[Task],
                                  res: ComplianceTest.ComplianceResult
                                ): TestResult = {
    val results = assert(())(res)
    if(results.isFailure) {
      Spec.

    }
      throw new weaver.CanceledException(
          Some(failures.head),
          weaver.SourceLocation.fromContext
        )

    case Right(_) => Spec.
        throw new weaver.IgnoredException(
          Some("Passing unknown spec"),
          weaver.SourceLocation.fromContext
        )
    }*/

}

// brought over from weaver https://github.com/disneystreaming/weaver-test/blob/d5489c994ecbe84f267550fb84c25c9fba473d70/modules/core/src/weaver/Filters.scala#L5
/*object Filters {

  def toPattern(filter: String): Pattern = {
    val parts = filter
      .split("\\*", -1)
      .map { // Don't discard trailing empty string, if any.
        case ""  => ""
        case str => Pattern.quote(str)
      }
    Pattern.compile(parts.mkString(".*"))
  }

  private type Predicate = String => Boolean

/*  private object atLine {
    def unapply(testPath: String): Option[(String, Int)] = {
      // Can't use string interpolation in pattern (2.12)
      val members = testPath.split(".line://")
      if (members.size == 2) {
        val suiteName = members(0)
        // Can't use .toIntOption (2.12)
        val maybeLine = scala.util.Try(members(1).toInt).toOption
        maybeLine.map(suiteName -> _)
      } else None
    }
  }*/

  /*  def filterTests(
                   suiteName: String
                 )(args: List[String]): String => Boolean = {

    def toPredicate(filter: String): Predicate = {
      filter match {

        case atLine(`suiteName`, line) => { case TestName(_, indicator, _) =>
          indicator.line == line
        }
        case regexStr => { case TestName(name, _, _) =>
          val fullName = suiteName + "." + name
          toPattern(regexStr).matcher(fullName).matches()
        }
      }
    }

    import scala.util.Try
    val maybePattern = for {
      index <- Option(args.indexOf("-o"))
        .orElse(Option(args.indexOf("--only")))
        .filter(_ >= 0)
      filter <- Try(args(index + 1)).toOption
    } yield toPredicate(filter)
    testId => maybePattern.forall(_.apply(testId))
  }
 */
}*/
