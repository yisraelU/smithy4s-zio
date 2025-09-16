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

// publish website from this branch
ThisBuild / tlSitePublishBranch := Some("main")

ThisBuild / tlCiHeaderCheck := false
ThisBuild / tlFatalWarnings := false
ThisBuild / tlCiMimaBinaryIssueCheck := false
ThisBuild / tlJdkRelease := Some(11)
ThisBuild / tlCiDependencyGraphJob := false

// Explicit Sonatype Central Portal configuration
ThisBuild / sonatypeCredentialHost := "central.sonatype.com"
ThisBuild / sonatypeRepository := "https://central.sonatype.com/api/v1/publisher"

// Note: For new Sonatype Central Portal troubleshooting:
// 1. Verify namespace at https://central.sonatype.com/
// 2. Ensure User Token is correctly set in GitHub secrets
// 3. Check that SONATYPE_CREDENTIAL_HOST = "central.sonatype.com"
