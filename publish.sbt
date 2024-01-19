ThisBuild / organization := "io.github.yisraelu.smithy4s-zio"
ThisBuild / versionScheme := Some("early-semver")

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/yisraelu/smithy4s-zio"),
    "scm:git@github.com:yisraelu/smithy4s-zio.git"
  )
)
ThisBuild /   githubWorkflowJavaVersions := List(JavaSpec.temurin("11"))
ThisBuild / githubWorkflowTargetTags ++= Seq("v*")
ThisBuild /     githubWorkflowPublishTargetBranches := Seq(
  RefPredicate.StartsWith(Ref.Tag("v")),
  RefPredicate.Equals(Ref.Branch("main"))
)

ThisBuild /   githubWorkflowPublish := Seq(
  WorkflowStep.Sbt(
    commands = List("ci-release"),
    name = Some("Publish project"),
    env = Map(
      "PGP_PASSPHRASE"    -> "${{ secrets.PGP_PASSPHRASE }}",
      "PGP_SECRET"        -> "${{ secrets.PGP_SECRET }}",
      "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
      "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}"
    )
  )
)

ThisBuild / developers := List(
  Developer(
    id = "yisraelu",
    name = "Yisrael Union",
    email = "ysrlunion@gmail.com",
    url = url("https://github.com/yisraelu")
  )
)

ThisBuild / description := "ZIO bindings for Smithy4s"
ThisBuild / licenses := List(
  "Apache 2" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt")
)
ThisBuild / homepage := Some(url("https://github.com/yisraelu/smithy4s-zio"))

// Remove all additional repository other than Maven Central from POM
ThisBuild / pomIncludeRepository := { _ => false }
ThisBuild / publishTo := {
  val nexus = "https://s01.oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}
ThisBuild / publishMavenStyle := true
