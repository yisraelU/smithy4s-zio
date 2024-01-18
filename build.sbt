import sbt.Project.projectToRef
import smithy4s.codegen.Smithy4sCodegenPlugin

import _root_.java.util.stream.Collectors
import java.nio.file.Files
import java.io.File
import sys.process.*
import scala.collection.Seq

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
val complianceTestDependencies =
  SettingKey[Seq[ModuleID]]("complianceTestDependencies")
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
  scenarios,
  `compliance-tests`,
  transformers
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
      Dependencies.Smithy4s.dynamic,
      Dependencies.Circe.parser,
      Dependencies.ZIO.catsInterop,
      Dependencies.Fs2Data.xml.value,
      Dependencies.LiHaoyi.pprint,
      Dependencies.ZIO.http,
      Dependencies.ZIO.test,
      Dependencies.ZIO.testSbt,
      Dependencies.ZIO.testMagnolia,
      Dependencies.Smithy.testTraits % Smithy4s
    ),
    Compile / smithy4sAllowedNamespaces := List("smithy.test"),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )
  .enablePlugins(Smithy4sCodegenPlugin)
lazy val http = (project in file("modules/http"))
  .dependsOn(
    scenarios,
    `compliance-tests` % "test->compile",
    transformers
  )
  .settings(
    name := s"$projectPrefix-http",
    libraryDependencies ++= Seq(
      Dependencies.Smithy4s.core,
      Dependencies.Smithy4s.json,
      Dependencies.Smithy4s.http4s,
      Dependencies.Alloy.core % Test,
      Dependencies.Smithy.testTraits % Test,
      Dependencies.Alloy.`protocol-tests` ,
      Dependencies.Typelevel.vault.value,
      Dependencies.ZIO.http,
      Dependencies.ZIO.test,
      Dependencies.ZIO.testSbt,
      Dependencies.Smithy4s.tests,
    Dependencies.Smithy.build % Test,
    ),
    Test / complianceTestDependencies := Seq(
      Dependencies.Alloy.`protocol-tests`
    ),
    (Test / smithy4sModelTransformers) := List("ProtocolTransformer"),
    Test / smithy4sAllowedNamespaces := List("smithy.test","aws","smithy4s.example.guides.auth",
      "aws.api",
      "aws.auth",
      "aws.customizations",
      "aws.protocols",
      "aws.protocoltests"
    ),

    (Test / resourceGenerators) := Seq(dumpModel(Test).taskValue),
    (Test / fork) := true,
    (Test / envVars) ++= {
      val files: Seq[File] = {
        (Test / resourceGenerators) {
          _.join.map(_.flatten)
        }.value
      }
      files.headOption
        .map { file =>
          Map("MODEL_DUMP" -> file.getAbsolutePath)
        }
        .getOrElse(Map.empty)
    },
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
    Compile / resourceDirectory := sourceDirectory.value / "resources",
    Compile / smithy4sOutputDir := sourceDirectory.value / "generated"
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

lazy val transformers = (project in file("modules/transformers"))
  .settings(
    name := s"$projectPrefix-transformers",
    libraryDependencies ++= Seq(
      Dependencies.Smithy.model,
      Dependencies.Smithy.build,
      Dependencies.Smithy.testTraits,
      Dependencies.Smithy.awsTraits,
      Dependencies.Alloy.core
    ),
    Compile / resourceDirectory := sourceDirectory.value / "resources"
  )


def dumpModel(config: Configuration) =
  Def.task {
    // fetch smithy4s
    lazy val cmd =
      ("cs" :: "install" :: "--channel" :: "https://disneystreaming.github.io/coursier.json" :: "smithy4s" :: Nil).!!

    val transforms = (config / smithy4sModelTransformers).value

    import sjsonnew._
    import BasicJsonProtocol._
    import sbt.FileInfo
    import sbt.HashFileInfo
    import sbt.io.Hash
    import scala.jdk.CollectionConverters._
    implicit val pathFormat: JsonFormat[File] =
      BasicJsonProtocol.projectFormat[File, HashFileInfo](
        p => {
          if (p.isFile()) FileInfo.hash(p)
          else
            // If the path is a directory, we get the hashes of all files
            // then hash the concatenation of the hash's bytes.
            FileInfo.hash(
              p,
              Hash(
                Files
                  .walk(p.toPath(), 2)
                  .collect(Collectors.toList())
                  .asScala
                  .map(_.toFile())
                  .map(Hash(_))
                  .foldLeft(Array.emptyByteArray)(_ ++ _)
              )
            )
        },
        hash => hash.file
      )
    val s = (config / streams).value

    lazy val modelTransformersCp = (transformers / Compile / fullClasspathAsJars).value
      .map(_.data)


    val localJars =  modelTransformersCp.filter(_.isFile).map(_.getAbsolutePath)
    val localJarsArg =
        if (localJars.isEmpty) List.empty
        else List("--local-jars", localJars.mkString(","))
    val args =
      if (transforms.isEmpty) List.empty
      else List("--transformers", transforms.mkString(","))

    val allArgs = localJarsArg ::: args
    val cached =
      Tracked.inputChanged[List[String], Seq[File]](
        s.cacheStoreFactory.make("input")
      ) {
        Function.untupled {
          Tracked
            .lastOutput[(Boolean, List[String]), Seq[File]](
              s.cacheStoreFactory.make("output")
            ) { case ((changed, deps), outputs) =>
              if (changed || outputs.isEmpty) {
                val cmd = ("smithy4s" :: "dump-model" :: deps ::: allArgs)
                  println(cmd)
                  val res = cmd.!!

                val file =
                  (config / resourceManaged).value / "compliance-tests.json"
                IO.write(file, res)
                Seq(file)

              } else {
                outputs.getOrElse(Seq.empty)
              }
            }
        }
      }

    val trackedFiles = List(
      "--dependencies",
      (config / complianceTestDependencies).?.value
        .getOrElse(Seq.empty)
        .map { moduleId =>
          s"${moduleId.organization}:${moduleId.name}:${moduleId.revision}"
        }
        .mkString(",")
    )

    cached(trackedFiles)

  }
