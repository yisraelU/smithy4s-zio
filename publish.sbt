import scala.collection.immutable.Seq

ThisBuild / tlBaseVersion := "0.0" // your current series x.y

ThisBuild / organization := "io.github.yisraelu"
ThisBuild / organizationName := "yisraelu"
ThisBuild / description := "ZIO bindings for Smithy4s"
ThisBuild / startYear := Some(2023)
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / developers := List(
  // your GitHub handle and name
  tlGitHubDev("yisraelu", "Yisrael Union")
)

// publish to s01.oss.sonatype.org (set to true to publish to oss.sonatype.org instead)
ThisBuild / tlSonatypeUseLegacyHost := false

// publish website from this branch
ThisBuild / tlSitePublishBranch := Some("main")

ThisBuild / tlCiHeaderCheck := false
ThisBuild / tlFatalWarnings := false
ThisBuild / tlCiMimaBinaryIssueCheck := false
ThisBuild / sonatypeProfileName := "io.github.yisraelu"
ThisBuild / tlJdkRelease := Some(11)
ThisBuild / tlCiDependencyGraphJob := false
