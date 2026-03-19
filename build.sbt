enablePlugins(ScalaJSPlugin)

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.8.2"

lazy val root = (project in file("."))
  .settings(
    name := "scala-js-jfx",
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "2.8.1",
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.ESModule))
  )

