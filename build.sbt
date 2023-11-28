import sbt.Project.projectToRef

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
  tests
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

lazy val http = (project in file("modules/http"))
  .settings(
    name := s"$projectPrefix-http",
    libraryDependencies ++= Seq(
      Dependencies.Smithy4s.core,
      Dependencies.Smithy4s.json,
      Dependencies.Smithy4s.http4s,
      Dependencies.Typelevel.vault.value,
      Dependencies.ZIO.http
    )
  )

lazy val tests = (project in file("modules/tests"))
  .dependsOn(http)
  .settings(
    name := s"$projectPrefix-tests",
    libraryDependencies ++= {
      Seq(
        Dependencies.Http4s.emberClient.value,
        Dependencies.Http4s.emberServer.value,
        Dependencies.Http4s.dsl.value,
        Dependencies.Http4s.circe.value,
        Dependencies.Weaver.cats.value,
        Dependencies.Smithy4s.complianceTests
      )
    }
  )

lazy val examples = (project in file("modules/examples"))
  .settings(
    name := s"$projectPrefix-examples",
    fork / run := true
  )
  .dependsOn(http, prelude)
  .enablePlugins(Smithy4sCodegenPlugin)
