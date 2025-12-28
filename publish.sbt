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

// Publish website from version tags (so docs always show latest released version)
ThisBuild / tlSitePublishBranch := None
ThisBuild / tlSitePublishTags := true

ThisBuild / tlCiHeaderCheck := false
ThisBuild / tlFatalWarnings := false
ThisBuild / tlCiMimaBinaryIssueCheck := false
ThisBuild / tlJdkRelease := Some(11)
ThisBuild / tlCiDependencyGraphJob := false
