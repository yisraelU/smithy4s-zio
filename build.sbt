import sbt.Project.projectToRef
import smithy4s.codegen.Smithy4sCodegenPlugin

ThisBuild / version := "0.1.0-SNAPSHOT"
Global / onChangedBuildSource := ReloadOnSourceChanges
ThisBuild / scalaVersion := "2.13.12"
addCommandAlias(
  "fmt",
  ";scalafmtAll;scalafmtSbt;"
)
addCompilerPlugin(
  "org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full
)

val projectPrefix = "smithy4s-zio"

lazy val root = project
  .in(file("."))
  .aggregate(allModules: _*)
  .enablePlugins(ScalafixPlugin)

lazy val allModules = Seq(
  http,
  prelude,
  schema,
  examples,
  scenarios
).map(projectToRef)

lazy val prelude = (project in file("modules/prelude"))
  .settings(
    name := s"$projectPrefix-prelude",
    libraryDependencies ++= Seq(
      Dependencies.Smithy4s.core,
      Dependencies.ZIO.prelude,
      Dependencies.ZIO.schema,
      Dependencies.ZIO.test,
      Dependencies.ZIO.testSbt,
      Dependencies.ZIO.testMagnolia
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )

lazy val schema = (project in file("modules/schema"))
  .settings(
    name := s"$projectPrefix-schema",
    libraryDependencies ++= Seq(
      Dependencies.Smithy4s.core,
      Dependencies.ZIO.schema,
      Dependencies.ZIO.test,
      Dependencies.ZIO.testSbt,
      Dependencies.ZIO.testMagnolia
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )

lazy val `compliance-tests` = (project in file("modules/compliance-tests"))
  .settings(
    name := s"$projectPrefix-compliance-test",
    libraryDependencies ++= Seq(
      Dependencies.Smithy4s.core,
      Dependencies.Smithy4s.json,
      Dependencies.Smithy4s.tests,
      Dependencies.ZIO.http,
      Dependencies.ZIO.test,
      Dependencies.ZIO.testSbt,
      Dependencies.ZIO.testMagnolia,
      Dependencies.Smithy.testTraits % Smithy4s
    ),
    Compile / smithy4sAllowedNamespaces := List("smithy.test"),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )
  .dependsOn(http)
  .enablePlugins(Smithy4sCodegenPlugin)
lazy val http = (project in file("modules/http"))
  .dependsOn(scenarios)
  .settings(
    name := s"$projectPrefix-http",
    libraryDependencies ++= Seq(
      Dependencies.Smithy4s.core,
      Dependencies.Smithy4s.json,
      Dependencies.Smithy4s.http4s,
      Dependencies.Typelevel.vault.value,
      Dependencies.ZIO.http,
      Dependencies.ZIO.test,
      Dependencies.ZIO.testSbt,
      Dependencies.Smithy4s.tests
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )
  .enablePlugins(ScalafixPlugin)

lazy val scenarios = (project in file("modules/test-scenarios"))
  .settings(
    name := s"$projectPrefix-tests",
    libraryDependencies ++= {
      Seq(
        Dependencies.Smithy4s.core
      )
    },
    Compile / smithy4sInputDirs := Seq(sourceDirectory.value / "smithy"),
    Compile / resourceDirectory :=  sourceDirectory.value  / "resources",
    Compile / smithy4sOutputDir := sourceDirectory.value / "generated",
  )
  .enablePlugins(Smithy4sCodegenPlugin)

lazy val examples = (project in file("modules/examples"))
  .settings(
    name := s"$projectPrefix-examples",
    fork / run := true,
    libraryDependencies ++= Seq(
      Dependencies.Smithy4s.http4s,
      Dependencies.Http4s.emberServer.value,
      Dependencies.ZIO.catsInterop
    )
  )
  .dependsOn(http, prelude)
  .enablePlugins(Smithy4sCodegenPlugin)
