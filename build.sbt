import org.scalajs.linker.interface.{ESVersion, ModuleKind}
import org.scalajs.sbtplugin.ScalaJSPlugin
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbt.url
import ScalaJsViteSupport._

ThisBuild / version := "3.0.0-SNAPSHOT"
ThisBuild / organization := "com.anjunar"
ThisBuild / organizationName := "Anjunar"
ThisBuild / organizationHomepage := Some(url("https://github.com/anjunar"))

ThisBuild / usePipelining := false
ThisBuild / exportJars := false
Global / concurrentRestrictions += Tags.limitAll(1)

ThisBuild / scalaVersion := "3.3.8"

ThisBuild / homepage := Some(url("https://github.com/anjunar/scalajs-jfx"))
ThisBuild / description := "Reactive UI framework for Scala.js with lifecycle control, typed forms, routing, tables, and a composable DSL."

ThisBuild / licenses := Seq("MIT" -> url("https://opensource.org/licenses/MIT"))

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/anjunar/scalajs-jfx"),
    "scm:git:https://github.com/anjunar/scalajs-jfx.git",
    Some("scm:git:git@github.com:anjunar/scalajs-jfx.git")
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
  val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
  if (version.value.endsWith("-SNAPSHOT"))
    Some("central-snapshots" at centralSnapshots)
  else
    localStaging.value
}

lazy val commonJsSettings = Seq(
  scalaJSLinkerConfig := {
    scalaJSLinkerConfig.value
      .withModuleKind(ModuleKind.ESModule)
      .withESFeatures(_.withESVersion(ESVersion.ES2021))
      .withSourceMap(true)
      .withRelativizeSourceMapBase(
        Some((Compile / fastLinkJS / scalaJSLinkerOutputDirectory).value.toURI)
      )
  }
)

lazy val commonLibrarySettings = Seq(
  Compile / doc / sources := Seq.empty,
  Compile / packageDoc / mappings +=
    ((LocalRootProject / baseDirectory).value / "README.md") -> "README.md",
  libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "2.8.1",
  libraryDependencies += "org.scalatest" %%% "scalatest" % "3.2.19" % Test
)

lazy val jfxCore = Project(id = "scalajs-jfx-core", base = file("jfx-core"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "scalajs-jfx-core",
    moduleName := "scalajs-jfx-core",
    libraryDependencies += "com.anjunar" %%% "scala-reflect" % "1.1.3"
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

lazy val jfxJson = Project(id = "scalajs-jfx-json", base = file("jfx-json"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(jfxCore)
  .settings(
    name := "scalajs-jfx-json",
    moduleName := "scalajs-jfx-json",
    libraryDependencies += "com.anjunar" %%% "scala-reflect" % "1.1.3"
  )
  .settings(commonLibrarySettings)
  .settings(commonJsSettings)

lazy val jfxControls = Project(id = "scalajs-jfx-controls", base = file("jfx-controls"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(jfxCore, jfxRouter, jfxViewport % "test->compile")
  .settings(
    name := "scalajs-jfx-controls",
    moduleName := "scalajs-jfx-controls",
    publish / skip := true
  )
  .settings(commonLibrarySettings)
  .settings(commonJsSettings)

lazy val jfxForms = Project(id = "scalajs-jfx-forms", base = file("jfx-forms"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(jfxCore, jfxControls, jfxViewport)
  .settings(
    name := "scalajs-jfx-forms",
    moduleName := "scalajs-jfx-forms",
    libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % "2.6.0"
  )
  .settings(commonLibrarySettings)
  .settings(commonJsSettings)

lazy val jfxEditor = Project(id = "scalajs-jfx-editor", base = file("jfx-editor"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(jfxForms)
  .settings(
    name := "scalajs-jfx-editor",
    moduleName := "scalajs-jfx-editor",
    libraryDependencies += "com.anjunar" %%% "scalajs-lexical" % "1.3.0",
    publish / skip := true
  )
  .settings(commonLibrarySettings)
  .settings(commonJsSettings)

lazy val jfxWebAuthn = Project(id = "scalajs-jfx-webauthn", base = file("jfx-webAuthn"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "scalajs-jfx-webauthn",
    moduleName := "scalajs-jfx-webauthn"
  )
  .settings(commonLibrarySettings)
  .settings(commonJsSettings)

lazy val app = Project(id = "scalajs-jfx-demo", base = file("application"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(
    jfxCore,
    jfxRouter,
    jfxViewport,
    jfxJson,
    jfxControls,
    jfxForms,
    jfxEditor,
    jfxWebAuthn
  )
  .settings(
    scalaJSUseMainModuleInitializer := false,
    viteFastLinkJS := {
      clearLegacyShadowSources(
        (Compile / fastLinkJS / scalaJSLinkerOutputDirectory).value,
        streams.value.log
      )
      val linked = (Compile / fastLinkJS).value
      sanitizeScalaJsSourceMap(
        (LocalRootProject / baseDirectory).value,
        (Compile / fastLinkJS / scalaJSLinkerOutputDirectory).value,
        streams.value.log
      )
      linked
    },
    viteFullLinkJS := {
      clearLegacyShadowSources(
        (Compile / fullLinkJS / scalaJSLinkerOutputDirectory).value,
        streams.value.log
      )
      val linked = (Compile / fullLinkJS).value
      sanitizeScalaJsSourceMap(
        (LocalRootProject / baseDirectory).value,
        (Compile / fullLinkJS / scalaJSLinkerOutputDirectory).value,
        streams.value.log
      )
      linked
    },
    Compile / fastLinkJS / scalaJSLinkerOutputDirectory :=
      baseDirectory.value / "target" / "vite" / "fastopt",
    Compile / fullLinkJS / scalaJSLinkerOutputDirectory :=
      baseDirectory.value / "target" / "vite" / "fullopt",
    publish / skip := true
  )
  .settings(commonJsSettings)

lazy val root = Project(id = "scalajs-jfx-root", base = file("."))
  .aggregate(
    jfxCore,
    jfxRouter,
    jfxViewport,
    jfxJson,
    jfxControls,
    jfxForms,
    jfxEditor,
    jfxWebAuthn,
    app
  )
  .settings(
    publish / skip := true
  )
