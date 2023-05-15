
ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.9"
addCompilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full)

lazy val root = (project in file(".")).aggregate(core)
 // .aggregate(core, example)
lazy val core = (project in file("core"))
  .settings(
    name := "Smithy4s-Zhttp",
    libraryDependencies ++= Seq(
      "com.disneystreaming.smithy4s" %% "smithy4s-core" % "dev-SNAPSHOT",
      "com.disneystreaming.smithy4s" %% "smithy4s-json" % "dev-SNAPSHOT",
      "org.typelevel" %% "cats-core" % "2.9.0",
     "dev.zio" %% "zio-http" % "3.0.0-RC1",
      compilerPlugin(
        "org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full
      )
    ),

  )

lazy val tests = (project in file("tests")).dependsOn(core)
  .settings(
    scalaVersion := "2.13.7",
    libraryDependencies ++= {
     Seq(
        Dependencies.Http4s.emberClient.value,
        Dependencies.Http4s.emberServer.value,

        Dependencies.Http4s.dsl.value,
        Dependencies.Http4s.circe.value,
        Dependencies.Weaver.cats.value,
       "io.d11" %% "zhttp" % "2.0.0-RC11",
        "com.disneystreaming.smithy4s" %% "smithy4s-compliance-tests" % "dev-SNAPSHOT" % Test,
      )
    },
  )
