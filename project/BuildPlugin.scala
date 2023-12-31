import sbt.AutoPlugin
import scalafix.sbt.ScalafixPlugin.autoImport._
import xerial.sbt.Sonatype.SonatypeKeys._
import sbt._
import sbt.Keys._
import com.jsuereth.sbtpgp.PgpKeys._
import sbt.internal.LogManager
import sbt.internal.util.BufferedAppender
import java.io.PrintStream
import sbt.internal.ProjectMatrix
import sbtprojectmatrix.ProjectMatrixPlugin.autoImport.virtualAxes
import org.scalajs.sbtplugin.ScalaJSPlugin
import scala.scalanative.sbtplugin.ScalaNativePlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.scalaJSLinkerConfig
import org.scalajs.linker.interface.ModuleKind
import org.scalajs.jsenv.nodejs.NodeJSEnv
import com.github.sbt.git.SbtGit.git
import java.time.OffsetDateTime

object BuildPlugin extends AutoPlugin {

  val Scala212 = "2.12.18"
  val Scala213 = "2.13.10"
  val Scala3 = "3.3.1"

  sealed trait Platform

  case object JSPlatform extends Platform

  case object NativePlatform extends Platform

  case object JVMPlatform extends Platform

  lazy val jvmDimSettings = simpleJVMLayout
  lazy val nativeDimSettings = simpleNativeLayout ++ Seq(
    Test / fork := false
  )

  lazy val simpleJSLayout = simpleLayout(JSPlatform)
  lazy val simpleJVMLayout = simpleLayout(JVMPlatform)
  lazy val simpleNativeLayout = simpleLayout(NativePlatform)

  // Mill-like simple layout
  def simpleLayout(
      platform: Platform,
      catsEffect: Boolean = false
  ): Seq[Setting[_]] = {

    val baseDir = Def.setting {
      sourceDirectory.value.getParentFile
    }

    val platformSuffix = Def.setting {
      platform match {
        case JVMPlatform    => Seq("-jvm", "-jvm-native", "-jvm-js")
        case JSPlatform     => Seq("-js", "-jvm-js", "-js-native")
        case NativePlatform => Seq("-native", "-jvm-native", "-js-native")
      }
    }

    val scalaVersionSuffix = Def
      .setting {
        scalaBinaryVersion.value match {
          case "2.11" => Seq("-2", "-2.11")
          case "2.12" => Seq("-2", "-2.12")
          case "2.13" => Seq("-2", "-2.13")
          case _      => Seq("-3")
        }
      }

    val crossCompilationDirs = Def.setting {
      val empty = Seq("")

      // god forbid we ever have to put files in these folders
      val crissCross = for {
        platform <- platformSuffix.value ++ empty
        version <- scalaVersionSuffix.value ++ empty
      } yield s"src$platform$version"
      crissCross
    }

    Seq(
      Compile / unmanagedSourceDirectories := Seq(
        baseDir.value / "src"
      ) ++ crossCompilationDirs.value.map(baseDir.value / _),
      Compile / unmanagedResourceDirectories := Seq(
        baseDir.value / "resources"
      ),
      Test / unmanagedSourceDirectories := Seq(
        baseDir.value / "test" / "src"
      ) ++ crossCompilationDirs.value.map(baseDir.value / "test" / _),
      Test / unmanagedResourceDirectories := Seq(
        baseDir.value / "test" / "resources"
      )
    )
  }

  lazy val compilerPlugins = Seq(
    libraryDependencies ++= {
      if (scalaVersion.value.startsWith("2."))
        Seq(
          compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
          compilerPlugin(
            "org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full
          )
        )
      else Seq.empty
    }
  )

  lazy val commonCompilerOptions = Seq(
    "-deprecation", // Emit warning and location for usages of deprecated APIs.
    "-encoding",
    "utf-8", // Specify character encoding used by source files.
    "-explaintypes", // Explain type errors in more detail.
    "-feature", // Emit warning and location for usages of features that should be imported explicitly.
    "-language:existentials", // Existential types (besides wildcard types) can be written and inferred
    "-language:experimental.macros", // Allow macro definition (besides implementation and application)
    "-language:higherKinds", // Allow higher-kinded types
    "-language:implicitConversions", // Allow definition of implicit functions called views
    "-unchecked", // Enable additional warnings where generated code depends on assumptions.
    "-Xcheckinit", // Wrap field accessors to throw an exception on uninitialized access.
    "-Xlint:adapted-args", // Warn if an argument list is modified to match the receiver.
    "-Xlint:constant", // Evaluation of a constant arithmetic expression results in an error.
    "-Xlint:delayedinit-select", // Selecting member of DelayedInit.
    "-Xlint:doc-detached", // A Scaladoc comment appears to be detached from its element.
    "-Xlint:inaccessible", // Warn about inaccessible types in method signatures.
    "-Xlint:infer-any", // Warn when a type argument is inferred to be `Any`.
    "-Xlint:missing-interpolator", // A string literal appears to be missing an interpolator id.
    "-Xlint:nullary-unit", // Warn when nullary methods return Unit.
    "-Xlint:option-implicit", // Option.apply used implicit view.
    "-Xlint:package-object-classes", // Class or object defined in package object.
    "-Xlint:poly-implicit-overload", // Parameterized overloaded implicit methods are not visible as view bounds.
    "-Xlint:private-shadow", // A private field (or class parameter) shadows a superclass field.
    "-Xlint:stars-align", // Pattern sequence wildcard must align with sequence component.
    "-Xlint:type-parameter-shadow", // A local type parameter shadows a type already in scope.
    "-Ywarn-dead-code", // Warn when dead code is identified.
    "-Ywarn-extra-implicit", // Warn when more than one implicit parameter section is defined.
    "-Ywarn-numeric-widen", // Warn when numerics are widened.
    "-Ywarn-unused:implicits", // Warn if an implicit parameter is unused.
    "-Ywarn-unused:imports", // Warn if an import selector is not referenced.
    "-Ywarn-unused:locals", // Warn if a local definition is unused.
    "-Ywarn-unused:patvars", // Warn if a variable bound in a pattern is unused.
    "-Ywarn-unused:privates", // Warn if a private member is unused.
    "-Ywarn-value-discard", // Warn when non-Unit expression results are unused.
    "-Xfatal-warnings" // Fail the compilation if there are any warnings.
  )

  lazy val compilerOptions2_12_Only =
    // These are unrecognized for Scala 2.13.
    Seq(
      "-Xfuture", // Turn on future language features.
      "-Xlint:by-name-right-associative", // By-name parameter of right associative operator.
      "-Xlint:nullary-override", // Warn when non-nullary `def f()' overrides nullary `def f'.
      "-Xlint:unsound-match", // Pattern match may not be typesafe.
      "-Yno-adapted-args", // Do not adapt an argument list (either by inserting () or creating a tuple) to match the receiver.
      "-Ypartial-unification", // Enable partial unification in type constructor inference
      "-Ywarn-inaccessible", // Warn about inaccessible types in method signatures.
      "-Ywarn-infer-any", // Warn when a type argument is inferred to be `Any`.
      "-Ywarn-nullary-override", // Warn when non-nullary `def f()' overrides nullary `def f'.
      "-Ywarn-nullary-unit" // Warn when nullary methods return Unit.
    )
}
