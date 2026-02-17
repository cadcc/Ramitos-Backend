addSbtPlugin("com.disneystreaming.smithy4s" % "smithy4s-sbt-codegen" % "0.19.0-M2")
addCompilerPlugin("org.scalameta" % "semanticdb-scalac" % "4.15.1" cross CrossVersion.full)
scalacOptions += "-Yrangepos"

