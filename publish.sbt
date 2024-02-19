ThisBuild / organization := "io.github.yisraelu.smithy4s-zio"
ThisBuild / versionScheme := Some("early-semver")

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/yisraelu/smithy4s-zio"),
    "scm:git@github.com:yisraelu/smithy4s-zio.git"
  )
)

inThisBuild(
  List(
    organization := "io.github.yisraelu",
    homepage := Some(url("https://github.com/smithy4s-zio")),
    description := "ZIO bindings for Smithy4s",
    // Alternatively License.Apache2 see https://github.com/sbt/librarymanagement/blob/develop/core/src/main/scala/sbt/librarymanagement/License.scala
    licenses := List(
      "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")
    ),
    developers := List(
      Developer(
        id = "yisraelu",
        name = "Yisrael Union",
        email = "ysrlunion@gmail.com",
        url = url("https://github.com/yisraelu")
      )
    )
  )
)
ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"
sonatypeRepository := "https://s01.oss.sonatype.org/service/local"
