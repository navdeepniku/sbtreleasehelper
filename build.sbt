organization := "com.github.navdeepniku"
name := "sbt-release-helper"
homepage := Some(url("https://github.com/navdeepniku/sbtreleasehelper"))
scmInfo := Some(ScmInfo(url("https://github.com/navdeepniku/sbtreleasehelper)"),
  "scm:git@github.com:navdeepniku/sbtreleasehelper.git"))
developers := List(Developer("navdeepniku", "Navdeep Poonia", "navdeepniku@gmail.com", url("https://github.com/navdeepniku")))
licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))
scalaVersion := "2.12.8"
publishMavenStyle := false
bintrayRepository := "sbt-plugins"

ThisBuild / publishTo := sonatypePublishToBundle.value
packagedArtifacts += ((artifact in makePom).value, makePom.value)

lazy val root = (project in file(".")).
  enablePlugins(SbtPlugin).
  settings(
    scalacOptions ++= Seq("-deprecation", "-feature"),
    addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.12")
  )

libraryDependencies ++= Seq(
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.10.1",
  "org.apache.httpcomponents" % "httpclient" % "4.5.10"
)
