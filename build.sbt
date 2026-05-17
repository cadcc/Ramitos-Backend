import smithy4s.codegen.Smithy4sCodegenPlugin

val scala3Version = "3.8.2"
val http4sVersion = "0.23.33"
val log4catsVersion = "2.7.1"
val jwtVersion = "11.0.3"
val circeVersion = "0.14.15"
val doobieVersion = "1.0.0-RC11"
val password4jVersion = "1.8.4"
val pureconfigVersion = "0.17.10"
val hikariVersion = "7.0.2"
val fs2Version = "3.12.2"

ThisBuild / organization := "cl.cadcc"
ThisBuild / version := "0.1.0-SNAPSHOT"

lazy val root = project
  .in(file("."))
  .enablePlugins(Smithy4sCodegenPlugin)
  .settings(
    name := "ramitos",
    scalaVersion := scala3Version,

    libraryDependencies ++= Seq(
      // hashing
      "com.password4j" % "password4j" % password4jVersion,

      // fs2
      "co.fs2" %% "fs2-io" % fs2Version,

      // kittens
      "org.typelevel" %% "kittens" % "3.5.0",

      // config
      "com.github.pureconfig" %% "pureconfig-core" % pureconfigVersion,
      "com.github.pureconfig" %% "pureconfig-ip4s" % pureconfigVersion,
      "com.github.pureconfig" %% "pureconfig-cats-effect" % pureconfigVersion,
      "com.github.pureconfig" %% "pureconfig-http4s" % pureconfigVersion,

      // hikari
      "com.zaxxer" % "HikariCP" % hikariVersion,

      // doobie
      "org.tpolecat" %% "doobie-core"     % doobieVersion,
      "org.tpolecat" %% "doobie-postgres" % doobieVersion,
      "org.tpolecat" %% "doobie-postgres-circe" % doobieVersion,
      "org.tpolecat" %% "doobie-specs2"   % doobieVersion,
      "org.tpolecat" %% "doobie-hikari"   % doobieVersion,
      "org.postgresql" % "postgresql" % "42.7.10",

      // json
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,

      // logging
      "org.typelevel" %% "log4cats-slf4j"   % log4catsVersion,
      "ch.qos.logback" % "logback-classic"  % "1.5.29",

      // jwt
      "com.github.jwt-scala" %% "jwt-core" % jwtVersion,
      "com.github.jwt-scala" %% "jwt-circe" % jwtVersion,

      // http4s
      "org.http4s" %% "http4s-ember-client" % http4sVersion,
      "org.http4s" %% "http4s-ember-server" % http4sVersion,
      "org.http4s" %% "http4s-dsl"          % http4sVersion,
      "org.http4s" %% "http4s-circe"        % http4sVersion,

      "io.github.arturaz" %% "doobie-typesafe" % "0.5.1",

      // smithy4s
      "com.disneystreaming.smithy4s" %% "smithy4s-http4s" % smithy4sVersion.value,
      "com.disneystreaming.smithy4s" %% "smithy4s-http4s-swagger" % smithy4sVersion.value,

      "org.scalameta" %% "munit" % "1.0.0" % Test,
    ),

    scalacOptions ++= Seq("-Wvalue-discard", "-Wnonunit-statement", "-feature"),

    smithy4sAllowedNamespaces := List("cl.cadcc.ramitos.schema"),
    Compile / smithy4sInputDirs := List((ThisBuild / baseDirectory).value / "src" / "main" / "smithy"),


    assembly / assemblyJarName := "ramitos.jar",
    assembly / mainClass := Some("cl.cadcc.ramitos.Main"),
    assembly / assemblyMergeStrategy := {
      case s if s.endsWith("module-info.class") => MergeStrategy.discard
      case s if s.endsWith("manifest") => MergeStrategy.first
      case s if s.endsWith("smithy4s.tracking.smithy") => MergeStrategy.first
      case x => MergeStrategy.defaultMergeStrategy(x)
    },
  )
