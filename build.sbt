val http4sVersion = "0.21.0-M5"
scalaVersion := "2.13.1"
name := "fs-bug"
testFrameworks += new TestFramework("utest.runner.Framework")
fork := true
libraryDependencies ++= List(
  "co.fs2" %% "fs2-core" % "2.0.0",
  "com.lihaoyi" %% "utest" % "0.7.1" % Test,
)
scalacOptions ++= List(
  "-deprecation",
  "-unchecked",
  "-feature",
  "-language:higherKinds",
  "-language:experimental.macros",
  "-language:existentials",
  "-Ywarn-value-discard",
  "-Ywarn-unused:imports",
  "-Ywarn-unused:implicits",
  "-Ywarn-unused:params",
  "-Ywarn-unused:patvars",
)
