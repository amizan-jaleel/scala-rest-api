ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.16"

lazy val root = (project in file("."))
  .settings(
    name := "scala-rest-api",
    idePackagePrefix := Some("org.amizan"),
    libraryDependencies ++= Seq(
      "com.twitter" %% "finagle-http" % "24.2.0",
      "com.typesafe.play" %% "play-json" % "2.9.4", // JSON library
      "ch.qos.logback" % "logback-classic" % "1.4.12", // Logging
      "com.twitter" %% "finatra-http-server" % "24.2.0",
    )
  )
