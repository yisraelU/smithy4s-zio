import sbt.Project.projectToRef

ThisBuild / version := "0.1.0-SNAPSHOT"
Global / onChangedBuildSource := ReloadOnSourceChanges
ThisBuild / scalaVersion := "2.13.10"
addCompilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full)

val projectPrefix = "smithy4s-zio"

lazy val root = project
  .in(file("."))
  .aggregate(allModules: _*)
  .enablePlugins(ScalafixPlugin)

lazy val allModules = Seq(
  http,
  prelude
).map(projectToRef)


lazy val prelude = (project in file("modules/prelude"))
  .settings(
    name := s"$projectPrefix-prelude",
    libraryDependencies ++= Seq(
      "com.disneystreaming.smithy4s" %% "smithy4s-core" % "0.18.1",
      // zio prelude
      "dev.zio" %% "zio-prelude" % "1.0.0-RC21",
        "dev.zio" %% "zio-test" % "2.0.18" % Test,
        "dev.zio" %% "zio-test-sbt" % "2.0.18" % Test,
        "dev.zio" %% "zio-test-magnolia" % "2.0.18" % Test),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
    )


lazy val http = (project in file("modules/http"))
  .settings(
    name := s"$projectPrefix-http",
    libraryDependencies ++= Seq(
      "com.disneystreaming.smithy4s" %% "smithy4s-core" % "0.18.1",
      "com.disneystreaming.smithy4s" %% "smithy4s-json" % "0.18.1",
      Dependencies.Typelevel.vault.value,
     "dev.zio" %% "zio-http" % "3.0.0-RC2",
      compilerPlugin(
        "org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full
      )
    ),

  )

lazy val tests = (project in file("modules/tests")).dependsOn(http)
  .settings(
    scalaVersion := "2.13.7",
    name := s"$projectPrefix-tests",
    libraryDependencies ++= {
     Seq(
        Dependencies.Http4s.emberClient.value,
        Dependencies.Http4s.emberServer.value,

        Dependencies.Http4s.dsl.value,
        Dependencies.Http4s.circe.value,
        Dependencies.Weaver.cats.value,
       "io.d11" %% "zhttp" % "2.0.0-RC11",
        "com.disneystreaming.smithy4s" %% "smithy4s-compliance-tests" % "0.18.1" % Test,
      )
    },
  )
