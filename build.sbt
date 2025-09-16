import sbt.Project.projectToRef
import smithy4s.codegen.{DumpModelArgs, Smithy4sCodegenPlugin}

import _root_.java.util.stream.Collectors
import java.nio.file.Files
import java.io.File
import sys.process.*
import scala.collection.Seq

Global / onChangedBuildSource := ReloadOnSourceChanges
val Scala213 = "2.13.16"
val Scala3 = "3.5.2"
val latest2ScalaVersions = List(Scala213, Scala3)
ThisBuild / scalaVersion := Scala213 // the default Scala

addCommandAlias(
  "lint",
  ";scalafmtAll;scalafmtSbt;scalafixAll;"
)
addCompilerPlugin(
  "org.typelevel" % "kind-projector" % "0.13.3" cross CrossVersion.full
)
val complianceTestDependencies =
  SettingKey[Seq[ModuleID]]("complianceTestDependencies")
val projectPrefix = "smithy4s-zio"

lazy val root = project
  .in(file("."))
  .aggregate(allModules: _*)
  .enablePlugins(ScalafixPlugin)

lazy val allModules = Seq(
  prelude,
  schema,
  http,
  examples,
  shared,
  scenarios,
  `compliance-tests`,
  `codegen-cli`,
  transformers,
  tests
).flatMap(_.projectRefs)

lazy val `codegen-cli` = (projectMatrix in file("modules/codegen-cli"))
  .jvmPlatform(Seq(Scala213))
  .settings(
    name := s"$projectPrefix-cli",
    libraryDependencies ++= Seq(
      Dependencies.Smithy4s.`codegen-cli`.value
    )
  )
  .enablePlugins(NoPublishPlugin)

lazy val prelude = (projectMatrix in file("modules/prelude"))
  .jvmPlatform(latest2ScalaVersions)
  .settings(
    name := s"$projectPrefix-prelude",
    libraryDependencies ++= Seq(
      Dependencies.Smithy4s.core.value,
      Dependencies.ZIO.prelude,
      Dependencies.ZIO.schema,
      Dependencies.ZIO.test,
      Dependencies.ZIO.testSbt,
      Dependencies.ZIO.testMagnolia
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )

lazy val schema = (projectMatrix in file("modules/schema"))
  .jvmPlatform(latest2ScalaVersions)
  .settings(
    name := s"$projectPrefix-schema",
    libraryDependencies ++= Seq(
      Dependencies.Smithy4s.core.value,
      Dependencies.ZIO.schema,
      Dependencies.ZIO.test,
      Dependencies.ZIO.testSbt,
      Dependencies.ZIO.testMagnolia
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )

lazy val `compliance-tests` =
  (projectMatrix in file("modules/compliance-tests"))
    .jvmPlatform(List(Scala213))
    .settings(
      name := s"$projectPrefix-compliance-test",
      libraryDependencies ++= Seq(
        Dependencies.Smithy4s.core.value,
        Dependencies.Smithy4s.json.value,
        Dependencies.Smithy4s.tests.value,
        Dependencies.Smithy4s.dynamic.value,
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
    .dependsOn(shared)
    .enablePlugins(Smithy4sCodegenPlugin, NoPublishPlugin)

lazy val http = (projectMatrix in file("modules/http"))
  .jvmPlatform(latest2ScalaVersions)
  .dependsOn(shared)
  .settings(
    name := s"$projectPrefix-http",
    libraryDependencies ++= Seq(
      Dependencies.Smithy4s.core.value,
      Dependencies.Smithy4s.json.value,
      Dependencies.ZIO.http
    )
  )
  .enablePlugins(ScalafixPlugin)

lazy val shared = (projectMatrix in file("modules/shared"))
  .jvmPlatform(latest2ScalaVersions)
  .settings(
    name := s"$projectPrefix-shared",
    Compile / smithy4sAllowedNamespaces := List("smithy.test"),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )
  .enablePlugins(NoPublishPlugin)

lazy val docs = projectMatrix
  .in(file("site"))
  .jvmPlatform(List(Scala213))
  .enablePlugins(TypelevelSitePlugin)
  .dependsOn(examples)

lazy val scenarios = (projectMatrix in file("modules/test-scenarios"))
  .jvmPlatform(Seq(Scala213))
  .settings(
    name := s"$projectPrefix-tests",
    libraryDependencies ++= {
      Seq(
        Dependencies.Smithy4s.core.value
      )
    },
    Compile / smithy4sInputDirs := Seq(sourceDirectory.value / "smithy"),
    Compile / resourceDirectory := sourceDirectory.value / "resources",
    Compile / smithy4sOutputDir := sourceDirectory.value / "generated"
  )
  .enablePlugins(Smithy4sCodegenPlugin, NoPublishPlugin)

lazy val examples = (projectMatrix in file("modules/examples"))
  .jvmPlatform(Seq(Scala213))
  .settings(
    name := s"$projectPrefix-examples",
    fork / run := true,
    libraryDependencies ++= Seq(
      Dependencies.Smithy4s.http4s.value,
      Dependencies.Http4s.emberServer.value,
      Dependencies.ZIO.catsInterop
    ),
    Compile / smithy4sAllowedNamespaces := List("example.todo", "example.hello")
  )
  .dependsOn(http)
  .enablePlugins(Smithy4sCodegenPlugin, NoPublishPlugin)

lazy val transformers = (projectMatrix in file("modules/transformers"))
  .jvmPlatform(latest2ScalaVersions)
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
  .enablePlugins(NoPublishPlugin)

lazy val tests = (projectMatrix in file("modules/tests"))
  .jvmPlatform(List(Scala213))
  .dependsOn(
    http,
    scenarios % "test->compile",
    `compliance-tests` % "test->compile",
    transformers % "test -> compile"
  )
  .settings(
    name := s"$projectPrefix-tests",
    libraryDependencies ++= Seq(
      Dependencies.ZIO.test,
      Dependencies.ZIO.testSbt,
      Dependencies.ZIO.http,
      Dependencies.Alloy.core % Test
    ),
    Test / complianceTestDependencies := Seq(
      Dependencies.Alloy.`protocol-tests`
    ),
    (Test / smithy4sModelTransformers) := List("ProtocolTransformer"),
    (Test / resourceGenerators) := Seq(dumpModel(Test).taskValue),
    (Test / fork) := true,
    Test / parallelExecution := false,
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
  .enablePlugins(NoPublishPlugin)

def dumpModel(config: Configuration): Def.Initialize[Task[Seq[File]]] =
  Def.task {
    val dumpModelCp =
      (`codegen-cli`.jvm(Scala213) / Compile / fullClasspath).value
        .map(_.data)

    val modelTransformersCp =
      (transformers.jvm(Scala213) / Compile / fullClasspath).value.map(_.data)
    val transforms = (config / smithy4sModelTransformers).value

    val cp = (if (transforms.isEmpty) dumpModelCp
              else dumpModelCp ++ modelTransformersCp)
      .map(_.getAbsolutePath())
      .mkString(":")

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
                val args =
                  if (transforms.isEmpty) List.empty
                  else List("--transformers", transforms.mkString(","))
                val res =
                  ("java" :: "-cp" :: cp :: "smithy4s.codegen.cli.Main" :: "dump-model" :: deps ::: args).!!
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
