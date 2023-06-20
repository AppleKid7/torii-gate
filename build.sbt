import NativePackagerHelper._

val scala3Version = "3.2.2"

val circeVersion = "0.14.3"
val monocleVersion = "3.1.0"
val logbackVersion = "1.4.5"
val zioVersion = "2.0.6"
val zioHttpVersion = "0.0.4"
val shardCakeVersion = "2.0.6"
val zioJsonVersion = "0.4.2"
val quillVersion = "4.6.0"
val zioConfigVersion = "3.0.7"
val testContainersVersion = "0.40.9"

lazy val root = project
  .in(file("."))
  .settings(
    name := "Torii Gate",
    organization := "com.torii-gate",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,
    scalacOptions ++= Seq(
      "-Xmax-inlines",
      "64",
      "-deprecation",
      "-encoding",
      "UTF-8",
      "-feature",
      "-language:higherKinds",
      "-language:existentials",
      "-unchecked",
      "-Xfatal-warnings",
      "-language:postfixOps",
      "-explain-types",
      "-Ykind-projector"
    ),
    Test / unmanagedClasspath += baseDirectory.value / "resources",
    Test / fork := true,
    Test / javaOptions += "--add-opens=java.base/java.util=ALL-UNNAMED",

    libraryDependencies ++= Seq(
      "io.circe" %% "circe-generic" % circeVersion,
      "ch.qos.logback" % "logback-classic" % logbackVersion % Runtime,
      "dev.zio" %% "zio" % zioVersion,
      "dev.zio" %% "zio-test" % zioVersion % Test,
      "dev.zio" %% "zio-test-sbt" % zioVersion % Test,
      "com.dimafeng" %% "testcontainers-scala-core" % testContainersVersion % Test,
      "dev.zio" %% "zio-streams" % zioVersion,
      "dev.zio" %% "zio-test-junit" % zioVersion,
      "dev.zio" %% "zio-http" % zioHttpVersion,
      "dev.zio" %% "zio-json" % zioJsonVersion,
      "dev.zio" %% "zio-config" % zioConfigVersion,
      "dev.zio" %% "zio-config-typesafe" % zioConfigVersion,
      "dev.zio" %% "zio-config-magnolia" % zioConfigVersion,
      "io.getquill" %% "quill-jdbc-zio" % quillVersion,
      "org.postgresql" % "postgresql" % "42.3.1",
      "com.devsisters" % "shardcake-core_3" % shardCakeVersion,
      "com.devsisters" %% "shardcake-manager" % shardCakeVersion,
      "com.devsisters" %% "shardcake-storage-redis" % shardCakeVersion,
      "com.devsisters" %% "shardcake-protocol-grpc" % shardCakeVersion,
      "com.devsisters" %% "shardcake-serialization-kryo" % shardCakeVersion,
      "org.scalameta" %% "munit" % "0.7.29" % Test,
    )
  )
