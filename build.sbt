ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.9"
addCompilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full)

lazy val root = (project in file("."))
  .aggregate(core, example)
lazy val core = (project in file("core"))
  .settings(
    name := "Smithy4s-Zhttp",
    libraryDependencies ++= Seq(
      "com.disneystreaming.smithy4s" %% "smithy4s-core" % "0.16.1",
      "com.disneystreaming.smithy4s" %% "smithy4s-json" % "0.16.1",
      "io.d11" %% "zhttp" % "2.0.0-RC11",
      compilerPlugin(
        "org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full
      )
    )
  )

lazy val example = (project in file("example"))
  .settings(
    name := "Smithy4s-Zhttp-Example",

  ).enablePlugins(Smithy4sCodegenPlugin)
  .dependsOn(core)