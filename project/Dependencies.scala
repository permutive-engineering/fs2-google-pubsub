import sbt._
import sbt.Keys._

object Dependencies {

  lazy val documentation = Seq(
    ("org.scalameta" %% "mdoc" % mdoc.BuildInfo.version).excludeAll(
      ExclusionRule(organization = "com.thesamet.scalapb", name = "lenses_2.13"),
      ExclusionRule(organization = "com.thesamet.scalapb", name = "scalapb-runtime_2.13")
    ),
    "com.permutive" %% "gcp-auth"            % "1.0.0",
    "org.http4s"    %% "http4s-ember-client" % "0.23.27"
  )

  lazy val `http4s-grpc` = "io.chrisdavenport" %% "http4s-grpc" % "0.0.4"

  lazy val grpc = Seq(
    "com.google.api.grpc" % "proto-google-cloud-pubsub-v1" % "1.113.0",
    "com.google.api.grpc" % "proto-google-common-protos"   % "2.41.0",
    "com.google.protobuf" % "protobuf-java"                % "4.27.2"
  ).map(_ % "protobuf-src" intransitive ()) ++ Seq(
    "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf"
  )

  lazy val `fs2-pubsub` = Seq(
    "co.fs2"        %% "fs2-core"                % "3.10.2",
    "com.permutive" %% "common-types-gcp-http4s" % "1.0.0",
    "io.circe"      %% "circe-parser"            % "0.14.7",
    "org.http4s"    %% "http4s-circe"            % "0.23.27",
    "org.http4s"    %% "http4s-client"           % "0.23.27",
    "org.http4s"    %% "http4s-dsl"              % "0.23.27"
  ) ++ Seq(
    "com.dimafeng"  %% "testcontainers-scala-munit" % "0.41.0",
    "com.permutive" %% "gcp-auth"                   % "1.0.0",
    "org.http4s"    %% "http4s-ember-client"        % "0.23.27",
    "org.slf4j"      % "slf4j-nop"                  % "2.0.13",
    "org.typelevel" %% "munit-cats-effect"          % "2.0.0"
  ).map(_ % Test)

  lazy val `fs2-pubsub-pureconfig` = Seq(
    "com.github.pureconfig" %% "pureconfig-http4s"           % "0.17.7",
    "com.permutive"         %% "common-types-gcp-pureconfig" % "1.0.0"
  )

}
