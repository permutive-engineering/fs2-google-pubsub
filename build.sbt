ThisBuild / tlBaseVersion := "0.22" // your current series x.y

ThisBuild / organization := "com.permutive"
ThisBuild / organizationName := "Permutive"
ThisBuild / organizationHomepage := Some(url("https://github.com/permutive"))
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / developers := List(
  tlGitHubDev("cremboc", "Paulius Imbrasas"),
  tlGitHubDev("TimWSpence", "Tim Spence"),
  tlGitHubDev("bastewart", "Ben Stewart"),
  tlGitHubDev("travisbrown", "Travis Brown")
)
ThisBuild / startYear := Some(2018)

ThisBuild / tlSonatypeUseLegacyHost := true

val Scala213 = "2.13.10"
ThisBuild / crossScalaVersions := Seq(Scala213, "2.12.19", "3.2.0")
ThisBuild / scalaVersion := Scala213 // the default Scala
ThisBuild / tlJdkRelease := Some(11)
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("11"))

lazy val root = tlCrossRootProject
  .aggregate(common, http, grpc)

lazy val common = project
  .in(file("fs2-google-pubsub"))
  .settings(
    name := "fs2-google-pubsub",
    libraryDependencies ++= Dependencies.commonDependencies,
    libraryDependencies ++= Dependencies.testsDependencies
  )

lazy val http = project
  .in(file("fs2-google-pubsub-http"))
  .dependsOn(common)
  .settings(
    name := "fs2-google-pubsub-http",
    libraryDependencies ++= Dependencies.httpDependencies,
    libraryDependencies ++= Dependencies.testsDependencies
  )

lazy val grpc = project
  .in(file("fs2-google-pubsub-grpc"))
  .dependsOn(common)
  .settings(
    name := "fs2-google-pubsub-grpc",
    libraryDependencies ++= Dependencies.grpcDependencies,
    libraryDependencies ++= Dependencies.testsDependencies
  )
