import sbt.{Def, ModuleID, *}

object Dependencies {
  object Http4s {
    val http4sVersion = Def.setting("0.23.16")

    val circe: Def.Initialize[ModuleID] =
      Def.setting("org.http4s" %% "http4s-circe" % http4sVersion.value)

    val emberServer: Def.Initialize[ModuleID] =
      Def.setting("org.http4s" %% "http4s-ember-server" % http4sVersion.value)
    val emberClient: Def.Initialize[ModuleID] =
      Def.setting("org.http4s" %% "http4s-ember-client" % http4sVersion.value)
    val dsl: Def.Initialize[ModuleID] =
      Def.setting("org.http4s" %% "http4s-dsl" % http4sVersion.value)
  }
  object Fs2Data {
    val xml: Def.Initialize[ModuleID] =
      Def.setting("org.gnieh" %% "fs2-data-xml" % "1.9.1")
  }
  object LiHaoyi {
    val sourcecode = "com.lihaoyi" %% "sourcecode" % "0.2.7"
    val pprint = "com.lihaoyi" %% "pprint" % "0.8.1"
  }

  object Circe {
    val version = "0.14.5"
    val core = "io.circe" %% "circe-core" % version
    val parser = "io.circe" %% "circe-parser" % version
  }

  object Smithy4s {
    val version = "0.18.5"
    val complianceTests =
      "com.disneystreaming.smithy4s" %% "smithy4s-compliance-tests" % version % Test
    val core = "com.disneystreaming.smithy4s" %% "smithy4s-core" % version
    val json = "com.disneystreaming.smithy4s" %% "smithy4s-json" % version
    val http4s = "com.disneystreaming.smithy4s" %% "smithy4s-http4s" % version
    val dynamic = "com.disneystreaming.smithy4s" %% "smithy4s-dynamic" % version
    val tests =
      "com.disneystreaming.smithy4s" %% "smithy4s-tests" % version % Test
  }

  object Smithy {
    val smithyVersion = "1.41.1"
    val org = "software.amazon.smithy"
    val testTraits = org % "smithy-protocol-test-traits" % smithyVersion
    val model = org % "smithy-model" % smithyVersion
    val build = org % "smithy-build" % smithyVersion
    val awsTraits = org % "smithy-aws-traits" % smithyVersion
    val waiters = org % "smithy-waiters" % smithyVersion

  }

  val Alloy = new {
    val org = "com.disneystreaming.alloy"
    val alloyVersion = "0.2.8"
    val core = org % "alloy-core" % alloyVersion
    val openapi = org %% "alloy-openapi" % alloyVersion
    val `protocol-tests` = org % "alloy-protocol-tests" % alloyVersion
  }

  object ZIO {
    val core = "dev.zio" %% "zio" % "2.0.19"
    val http = "dev.zio" %% "zio-http" % "3.0.0-RC4"
    val prelude = "dev.zio" %% "zio-prelude" % "1.0.0-RC21"
    val schema = "dev.zio" %% "zio-schema" % "0.4.15"
    val catsInterop = "dev.zio" %% "zio-interop-cats" % "23.1.0.0"
    val test = "dev.zio" %% "zio-test" % "2.0.19"
    val testSbt = "dev.zio" %% "zio-test-sbt" % "2.0.19" % Test
    val testMagnolia = "dev.zio" %% "zio-test-magnolia" % "2.0.18" % Test
  }
  object Typelevel {
    val vault: Def.Initialize[ModuleID] =
      Def.setting("org.typelevel" %% "vault" % "3.5.0")
  }

  object Weaver {

    val weaverVersion =
      Def.setting("0.8.0")

    val cats: Def.Initialize[ModuleID] =
      Def.setting("com.disneystreaming" %% "weaver-cats" % weaverVersion.value)

    val scalacheck: Def.Initialize[ModuleID] =
      Def.setting(
        "com.disneystreaming" %% "weaver-scalacheck" % weaverVersion.value
      )
  }

  val ApiRegistryLib = new {
    val org = "com.disneystreaming.api.registry.lib"

    val registryLibVersion = "0.2.6"

    val dslibTraits = org % "dslib-traits" % registryLibVersion
    val openApi = org %% "openapi" % registryLibVersion
    val openapiDiscovery = org % "openapi-discovery" % registryLibVersion
  }
  val ApiRegistry = new {
    val org = "com.disneystreaming.api.registry"
    // Version value is called registryVersion to make it steward friendly
    val registryVersion = "2022.11.01.635"

    val dslib = org % "dslib" % registryVersion
    val pizza = org % "examples.pizza" % registryVersion
  }

}
