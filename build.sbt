import smithy4s.codegen.Smithy4sCodegenPlugin

val scala3Version = "3.8.1"
val http4sVersion = "0.23.33"
val log4catsVersion = "2.7.1"

ThisBuild / organization := "cl.cadcc"
ThisBuild / version := "0.1.0-SNAPSHOT"

lazy val root = project
  .in(file("."))
  .enablePlugins(Smithy4sCodegenPlugin)
  .settings(
    name := "ramitos",
    scalaVersion := scala3Version,

    libraryDependencies ++= Seq(
      // logging
      "org.typelevel" %% "log4cats-slf4j"   % log4catsVersion,
      "ch.qos.logback" % "logback-classic"  % "1.5.29", // TODO: remove to let users select their logging implementation

      // http4s
      "org.http4s" %% "http4s-ember-client" % http4sVersion,
      "org.http4s" %% "http4s-ember-server" % http4sVersion,
      "org.http4s" %% "http4s-dsl"          % http4sVersion,

      "io.github.arturaz" %% "doobie-typesafe" % "0.5.1",

      // smithy4s
      "com.disneystreaming.smithy4s" %% "smithy4s-http4s" % smithy4sVersion.value,
      "com.disneystreaming.smithy4s" %% "smithy4s-http4s-swagger" % smithy4sVersion.value,

      "org.scalameta" %% "munit" % "1.0.0" % Test,
    ),

    smithy4sAllowedNamespaces := List("cl.cadcc.ramitos.schema"),
    Compile / smithy4sInputDirs := List((ThisBuild / baseDirectory).value / "src" / "main" / "smithy"),
  )
