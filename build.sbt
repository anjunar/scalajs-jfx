import org.scalajs.linker.interface.{ESVersion, ModuleKind}
import org.scalajs.sbtplugin.ScalaJSPlugin

ThisBuild / version := "1.0.0"
ThisBuild / organization := "com.anjunar"
ThisBuild / organizationName := "Anjunar"
ThisBuild / organizationHomepage := Some(url("https://github.com/anjunar"))
ThisBuild / scalaVersion := "3.8.3"

lazy val commonJsSettings = Seq(
  scalaJSLinkerConfig ~= (
    _.withModuleKind(ModuleKind.ESModule)
      .withESFeatures(_.withESVersion(ESVersion.ES2021))
    )
)

lazy val jfx = (project in file("jfx"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "scala-js-jfx",
    moduleName := "scala-js-jfx",
    libraryDependencies += "com.anjunar" %%% "scala-reflect" % "1.0.0",
    libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "2.8.1",
    libraryDependencies += ("org.scala-js" %%% "scalajs-java-securerandom" % "1.0.0").cross(CrossVersion.for3Use2_13)
  )
  .settings(commonJsSettings)

lazy val app = (project in file("app"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(jfx)
  .settings(
    scalaJSUseMainModuleInitializer := true
  )
  .settings(commonJsSettings)

lazy val root = (project in file("."))
  .aggregate(jfx, app)
  .settings(
    publish / skip := true
  )