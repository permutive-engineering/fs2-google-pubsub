import sbt._

object Dependencies {
  object Versions {
    val scala212       = "2.12.14"
    val scala213       = "2.13.6"
    val catsCore       = "2.6.1"
    val effect         = "3.2.9"
    val fs2            = "3.1.2"
    val http4s         = "0.23.3"
    val log4cats       = "2.1.1"
    val jwt            = "3.18.2"
    val jsoniter       = "2.10.2"
    val gcp            = "1.115.5"
    val scalatest      = "3.2.9"
    val scalatestPlus  = "3.2.2.0"
    val testContainers = "0.39.7"
  }

  object Libraries {
    val catsCore      = "org.typelevel" %% "cats-core"          % Versions.catsCore
    val alleyCatsCore = "org.typelevel" %% "alleycats-core"     % Versions.catsCore
    val effect        = "org.typelevel" %% "cats-effect-kernel" % Versions.effect
    val fs2           = "co.fs2"        %% "fs2-core"           % Versions.fs2

    val http4sDsl    = "org.http4s" %% "http4s-dsl"           % Versions.http4s
    val http4sClient = "org.http4s" %% "http4s-client"        % Versions.http4s
    val http4sHttp   = "org.http4s" %% "http4s-okhttp-client" % Versions.http4s % Test

    val log4cats      = "org.typelevel" %% "log4cats-core"  % Versions.log4cats
    val log4catsSlf4j = "org.typelevel" %% "log4cats-slf4j" % Versions.log4cats % Test
    val slf4j         = "org.slf4j"      % "slf4j-simple"   % "1.7.32"          % Test

    val jwt = "com.auth0"        % "java-jwt"            % Versions.jwt
    val gcp = "com.google.cloud" % "google-cloud-pubsub" % Versions.gcp

    val jsoniterCore = "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core" % Versions.jsoniter % Compile
    val jsoniterMacros =
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % Versions.jsoniter % Provided

    val testContainers = "com.dimafeng"      %% "testcontainers-scala-scalatest" % Versions.testContainers % Test
    val scalatest      = "org.scalatest"     %% "scalatest"                      % Versions.scalatest      % Test
    val scalatestPlus  = "org.scalatestplus" %% "scalacheck-1-14"                % Versions.scalatestPlus  % Test
  }

  lazy val testsDependencies = Seq(
    Libraries.scalatest,
    Libraries.scalatestPlus,
    Libraries.http4sHttp,
    Libraries.log4cats,
    Libraries.log4catsSlf4j,
    Libraries.slf4j,
    Libraries.testContainers,
    Libraries.gcp % Test,
  )

  lazy val commonDependencies = Seq(
    Libraries.catsCore,
    Libraries.effect,
    Libraries.fs2,
  )

  lazy val httpDependencies = Seq(
    Libraries.alleyCatsCore,
    Libraries.http4sDsl,
    Libraries.http4sClient,
    Libraries.log4cats,
    Libraries.jwt,
    Libraries.jsoniterCore,
    Libraries.jsoniterMacros,
  )

  lazy val grpcDependencies = Seq(
    Libraries.gcp,
  )
}
