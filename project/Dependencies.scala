import sbt.{Def, ModuleID, *}
import smithy4s.codegen.Smithy4sCodegenPlugin.autoImport.smithy4sVersion

object Dependencies {
  object Http4s {
    val http4sVersion = Def.setting("0.23.33")

    val circe: Def.Initialize[ModuleID] =
      Def.setting("org.http4s" %% "http4s-circe" % http4sVersion.value)

    val emberServer: Def.Initialize[ModuleID] =
      Def.setting("org.http4s" %% "http4s-ember-server" % http4sVersion.value)
    val emberClient: Def.Initialize[ModuleID] =
      Def.setting("org.http4s" %% "http4s-ember-client" % http4sVersion.value)
    val core: Def.Initialize[ModuleID] =
      Def.setting("org.http4s" %% "http4s-core" % http4sVersion.value)
    val dsl: Def.Initialize[ModuleID] =
      Def.setting("org.http4s" %% "http4s-dsl" % http4sVersion.value)
  }
  object Fs2Data {
    val xml: Def.Initialize[ModuleID] =
      Def.setting("org.gnieh" %% "fs2-data-xml" % "1.12.0")
  }
  object LiHaoyi {
    val sourcecode = "com.lihaoyi" %% "sourcecode" % "0.4.2"
    val pprint = "com.lihaoyi" %% "pprint" % "0.9.6"
  }

  object Circe {
    val version = "0.14.14"
    val core = "io.circe" %% "circe-core" % version
    val parser = "io.circe" %% "circe-parser" % version
  }

  object Smithy4s {
    val version = smithy4sVersion
    val complianceTests = Def.setting(
      "com.disneystreaming.smithy4s" %% "smithy4s-compliance-tests" % version.value % Test
    )
    val core = Def.setting(
      "com.disneystreaming.smithy4s" %% "smithy4s-core" % version.value
    )
    val `codegen-cli` = Def.setting(
      "com.disneystreaming.smithy4s" %% "smithy4s-codegen-cli" % version.value
    )
    val json = Def.setting(
      "com.disneystreaming.smithy4s" %% "smithy4s-json" % version.value
    )
    val http4s = Def.setting(
      "com.disneystreaming.smithy4s" %% "smithy4s-http4s" % version.value
    )
    val dynamic = Def.setting(
      "com.disneystreaming.smithy4s" %% "smithy4s-dynamic" % version.value
    )
    val tests = Def.setting(
      "com.disneystreaming.smithy4s" %% "smithy4s-tests" % version.value % Test
    )
  }

  object Smithy {
    val smithyVersion = "1.61.0"
    val org = "software.amazon.smithy"
    val testTraits = org % "smithy-protocol-test-traits" % smithyVersion
    val model = org % "smithy-model" % smithyVersion
    val build = org % "smithy-build" % smithyVersion
    val awsTraits = org % "smithy-aws-traits" % smithyVersion
    val waiters = org % "smithy-waiters" % smithyVersion

  }

  val Alloy = new {
    val org = "com.disneystreaming.alloy"
    val alloyVersion = "0.3.35"
    val core = org % "alloy-core" % alloyVersion
    val openapi = org %% "alloy-openapi" % alloyVersion
    val `protocol-tests` = org % "alloy-protocol-tests" % alloyVersion
  }

  object ZIO {
    val zioVersion = "2.1.24"
    val schemaVersion = "1.7.6"
    val core = "dev.zio" %% "zio" % zioVersion

    val http = "dev.zio" %% "zio-http" % "3.7.4"
    val prelude = "dev.zio" %% "zio-prelude" % "1.0.0-RC44"
    val schema = "dev.zio" %% "zio-schema" % schemaVersion
    val catsInterop = "dev.zio" %% "zio-interop-cats" % "23.1.0.13"
    val test = "dev.zio" %% "zio-test" % zioVersion
    val testSbt = "dev.zio" %% "zio-test-sbt" % zioVersion % Test
    val testMagnolia = "dev.zio" %% "zio-test-magnolia" % zioVersion % Test
  }

}
