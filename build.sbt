import org.scalajs.linker.interface.{ESVersion, ModuleKind}
import org.scalajs.sbtplugin.ScalaJSPlugin
import sbt.url

version := "1.0.0-SNAPSHOT"
organization := "com.anjunar"
organizationName := "Anjunar"
organizationHomepage := Some(url("https://github.com/anjunar"))

usePipelining := false
exportJars := false
concurrentRestrictions += Tags.limitAll(1)

scalaVersion := "3.3.8"

homepage := Some(url("https://github.com/anjunar/scalajs-jfx"))
description := "Reactive UI framework for Scala.js with lifecycle control, typed forms, routing, tables, and a composable DSL."

licenses := Seq(License.MIT)

scmInfo := Some(
  ScmInfo(
    url("https://github.com/anjunar/scalajs-jfx"),
    "scm:git:https://github.com/anjunar/scalajs-jfx.git",
    Some("scm:git:git@github.com:anjunar/scalajs-jfx.git")
  )
)

developers := List(
  Developer(
    id = "anjunar",
    name = "Patrick Bittner",
    email = "anjunar@gmx.de",
    url = url("https://github.com/anjunar")
  )
)

versionScheme := Some("early-semver")

pomIncludeRepository := { _ => false }
publishMavenStyle := true

publishTo := {
  val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
  if version.value.endsWith("-SNAPSHOT") then
    Some("central-snapshots" at centralSnapshots)
  else
    localStaging.value
}

lazy val commonJsSettings = Seq(
  scalaJSLinkerConfig ~= (
    _.withModuleKind(ModuleKind.ESModule)
      .withESFeatures(_.withESVersion(ESVersion.ES2021))
    )
)

lazy val commonLibrarySettings = Seq(
  Compile / doc / sources := Seq.empty,
  Compile / packageDoc / mappings += {
    val converter = fileConverter.value
    val readme = ((LocalRootProject / baseDirectory).value / "README.md").toPath
    converter.toVirtualFile(readme) -> "README.md"
  },
  libraryDependencies += "org.scala-js" %% "scalajs-dom" % "2.8.1",
  libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % Test
)

lazy val jfxCore = Project(id = "scalajs-jfx-core", base = file("jfx-core"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "scalajs-jfx-core",
    moduleName := "scalajs-jfx-core",
    libraryDependencies += "com.anjunar" %% "scala-reflect" % "1.1.3"
  )
  .settings(commonLibrarySettings)
  .settings(commonJsSettings)

lazy val jfxRouter = Project(id = "scalajs-jfx-router", base = file("jfx-router"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(jfxCore)
  .settings(
    name := "scalajs-jfx-router",
    moduleName := "scalajs-jfx-router"
  )
  .settings(commonLibrarySettings)
  .settings(commonJsSettings)

lazy val jfxViewport = Project(id = "scalajs-jfx-viewport", base = file("jfx-viewport"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(jfxCore)
  .settings(
    name := "scalajs-jfx-viewport",
    moduleName := "scalajs-jfx-viewport"
  )
  .settings(commonLibrarySettings)
  .settings(commonJsSettings)

lazy val jfxI18n = Project(id = "scalajs-jfx-i18n", base = file("jfx-i18n"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(jfxCore)
  .settings(
    name := "scalajs-jfx-i18n",
    moduleName := "scalajs-jfx-i18n"
  )
  .settings(commonLibrarySettings)
  .settings(commonJsSettings)

lazy val jfxJson = Project(id = "scalajs-jfx-json", base = file("jfx-json"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(jfxCore)
  .settings(
    name := "scalajs-jfx-json",
    moduleName := "scalajs-jfx-json",
    libraryDependencies += "com.anjunar" %% "scala-reflect" % "1.1.3"
  )
  .settings(commonLibrarySettings)
  .settings(commonJsSettings)

lazy val jfxControls = Project(id = "scalajs-jfx-controls", base = file("jfx-controls"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(jfxCore, jfxRouter, jfxViewport % "test->compile")
  .settings(
    name := "scalajs-jfx-controls",
    moduleName := "scalajs-jfx-controls"
  )
  .settings(commonLibrarySettings)
  .settings(commonJsSettings)

lazy val jfxForms = Project(id = "scalajs-jfx-forms", base = file("jfx-forms"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(jfxCore, jfxControls, jfxViewport)
  .settings(
    name := "scalajs-jfx-forms",
    moduleName := "scalajs-jfx-forms"
  )
  .settings(commonLibrarySettings)
  .settings(commonJsSettings)

lazy val jfxEditor = Project(id = "scalajs-jfx-editor", base = file("jfx-editor"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(jfxForms)
  .settings(
    name := "scalajs-jfx-editor",
    moduleName := "scalajs-jfx-editor",
    libraryDependencies += "com.anjunar" %% "scalajs-lexical" % "1.3.0"
  )
  .settings(commonLibrarySettings)
  .settings(commonJsSettings)

lazy val jfxWebAuthn = Project(id = "scalajs-jfx-webauthn", base = file("jfx-webAuthn"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(jfxCore)
  .settings(
    name := "scalajs-jfx-webauthn",
    moduleName := "scalajs-jfx-webauthn"
  )
  .settings(commonLibrarySettings)
  .settings(commonJsSettings)

lazy val jfxSsr = Project(id = "scalajs-jfx-ssr", base = file("jfx-ssr"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(jfxCore)
  .settings(
    name := "scalajs-jfx-ssr",
    moduleName := "scalajs-jfx-ssr"
  )
  .settings(commonLibrarySettings)
  .settings(commonJsSettings)

lazy val app = Project(id = "scalajs-jfx-demo", base = file("application"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(
    jfxCore,
    jfxRouter,
    jfxViewport,
    jfxI18n,
    jfxJson,
    jfxControls,
    jfxForms,
    jfxEditor,
    jfxWebAuthn,
    jfxSsr
  )
  .settings(
    scalaJSUseMainModuleInitializer := false,
    publish / skip := true
  )
  .settings(commonJsSettings)

lazy val root = Project(id = "scalajs-jfx-root", base = file("."))
  .aggregate(
    jfxCore,
    jfxRouter,
    jfxViewport,
    jfxI18n,
    jfxJson,
    jfxControls,
    jfxForms,
    jfxEditor,
    jfxWebAuthn,
    jfxSsr,
    app
  )
  .settings(
    publish / skip := true
  )