import org.scalajs.linker.interface.{ESVersion, ModuleKind}
import org.scalajs.sbtplugin.ScalaJSPlugin

ThisBuild / version := "1.0.4"
ThisBuild / organization := "com.anjunar"
ThisBuild / organizationName := "Anjunar"
ThisBuild / organizationHomepage := Some(url("https://github.com/anjunar"))
ThisBuild / scalaVersion := "3.8.3"
ThisBuild / homepage := Some(url("https://github.com/anjunar/scala-js-jfx"))
ThisBuild / description := "Reactive UI framework for Scala.js with structured state, lifecycle control, and a composable DSL."
ThisBuild / licenses := Seq("MIT" -> url("https://opensource.org/licenses/MIT"))
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/anjunar/scala-js-jfx"),
    "scm:git:https://github.com/anjunar/scala-js-jfx.git",
    Some("scm:git:git@github.com:anjunar/scala-js-jfx.git")
  )
)
ThisBuild / developers := List(
  Developer(
    id = "anjunar",
    name = "Patrick Bittner",
    email = "anjunar@gmx.de",
    url = url("https://github.com/anjunar")
  )
)
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / pomIncludeRepository := { _ => false }
ThisBuild / publishMavenStyle := true
ThisBuild / publishTo := {
  if (isSnapshot.value) {
    Some("central-snapshots" at "https://central.sonatype.com/repository/maven-snapshots/")
  } else {
    localStaging.value
  }
}

lazy val commonJsSettings = Seq(
  scalaJSLinkerConfig ~= (
    _.withModuleKind(ModuleKind.ESModule)
      .withESFeatures(_.withESVersion(ESVersion.ES2021))
    )
)

lazy val jfx = Project(id = "scala-js-jfx", base = file("jfx"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "scala-js-jfx",
    moduleName := "scala-js-jfx",
    Compile / doc / sources := Seq.empty,
    libraryDependencies += "com.anjunar" %%% "scala-reflect" % "1.0.0",
    libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "2.8.1",
    libraryDependencies += ("org.scala-js" %%% "scalajs-java-securerandom" % "1.0.0").cross(CrossVersion.for3Use2_13),
    libraryDependencies += "org.scalatest" %%% "scalatest" % "3.2.19" % Test
  )
  .settings(commonJsSettings)

lazy val app = Project(id = "scala-js-jfx-demo", base = file("app"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(jfx)
  .settings(
    scalaJSUseMainModuleInitializer := true,
    publish / skip := true
  )
  .settings(commonJsSettings)

lazy val root = Project(id = "scala-js-jfx-root", base = file("."))
  .aggregate(jfx, app)
  .settings(
    publish / skip := true
  )
