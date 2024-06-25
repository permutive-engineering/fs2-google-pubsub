ThisBuild / scalaVersion           := "2.13.14"
ThisBuild / crossScalaVersions     := Seq("2.12.19", "2.13.14", "3.3.3")
ThisBuild / organization           := "com.permutive"
ThisBuild / versionPolicyIntention := Compatibility.BinaryCompatible

addCommandAlias("ci-test", "fix --check; versionPolicyCheck; mdoc; publishLocal; +test")
addCommandAlias("ci-docs", "github; mdoc; headerCreateAll")
addCommandAlias("ci-publish", "versionCheck; github; ci-release")

lazy val documentation = project
  .enablePlugins(MdocPlugin)
  .dependsOn(`fs2-pubsub-pureconfig`)
  .settings(mdocAutoDependency := false)
  .settings(libraryDependencies ++= Dependencies.documentation)

lazy val `fs2-pubsub` = module
  .enablePlugins(Http4sGrpcPlugin)
  .settings(libraryDependencies ++= Dependencies.`fs2-pubsub`)
  .settings(libraryDependencies ++= scalaVersion.value.on(2, 13)(Dependencies.grpc).getOrElse(Nil))
  .settings(libraryDependencies ++= scalaVersion.value.on(3)(Dependencies.grpc).getOrElse(Nil))
  .settings(libraryDependencies -= scalaVersion.value.on(2, 12)(Dependencies.`http4s-grpc`))
  .settings(PB.generate / excludeFilter := "package.proto")
  .settings(scalacOptions += "-Wconf:src=src_managed/.*:s")
  .settings(Compile / PB.targets += scalapb.gen(grpc = false) -> (Compile / sourceManaged).value / "scalapb")
  .settings(Compile / PB.targets := (if (scalaVersion.value.startsWith("2.12")) Nil else (Compile / PB.targets).value))
  .settings(Test / fork := true)
  .settings(Test / run / fork := true)

lazy val `fs2-pubsub-pureconfig` = module
  .dependsOn(`fs2-pubsub`)
  .settings(libraryDependencies ++= Dependencies.`fs2-pubsub-pureconfig`)
