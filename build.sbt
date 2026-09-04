import org.scalajs.linker.interface.{ESVersion, ModuleKind}
import org.scalajs.sbtplugin.ScalaJSPlugin
import sbt.url
import ScalaJsViteSupport.*

// ---------------------------------------------------------------------------
// sbt 2.x
//
// Migrationsentscheidungen, die man beim Lesen kennen muss:
//
// 1. `%%%` gibt es nicht mehr. sbt 2 kennt ein `platform`-Setting, damit traegt
//    `%%` den Plattform-Suffix (`_sjs1_3`) selbst. `sbt-platform-deps` ist damit
//    ueberfluessig und der zugehoerige Import entfaellt.
//
// 2. Keine `ThisBuild /`-Praefixe mehr. In sbt 2 sind blanke Settings in
//    build.sbt "common settings", die in *alle* Subprojekte injiziert werden.
//    Das ersetzt die frueheren ThisBuild-Settings mit besserer Delegation.
//    Achtung beim Ergaenzen: ein blankes Setting gilt jetzt ueberall, nicht nur
//    fuer das Root-Projekt. Root-spezifisches gehoert an `LocalRootProject /`.
//
// 3. Tasks sind in sbt 2 standardmaessig gecached. Ein Task-Ergebnistyp ohne
//    `sjsonnew.JsonFormat` laesst den Build beim Laden scheitern. `Attributed[Report]`
//    von Scala.js hat keinen — deshalb steht `viteFullLinkJS` in
//    `Def.uncached { ... }`. Das ist hier ohnehin richtig: der Task hat
//    Seiteneffekte auf dem Dateisystem (Sourcemap-Sanitizing).
//
// 4. Slash-Syntax ist Pflicht, 0.13-Syntax ist entfernt. War hier schon so.
// ---------------------------------------------------------------------------

version              := "3.0.0-SNAPSHOT"
organization         := "com.anjunar"
organizationName     := "Anjunar"
organizationHomepage := Some(url("https://github.com/anjunar"))

scalaVersion := "3.3.8"

homepage := Some(url("https://github.com/anjunar/scalajs-jfx"))
description := "Reactive UI framework for Scala.js with lifecycle control, typed forms, routing, tables, and a composable DSL."

licenses := Seq("MIT" -> url("https://opensource.org/licenses/MIT"))

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
publishMavenStyle    := true

publishTo := {
  val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
  if (version.value.endsWith("-SNAPSHOT"))
    Some("central-snapshots" at centralSnapshots)
  else
    localStaging.value
}

// --- Bewusst entfernte sbt-1-Settings ---------------------------------------
//
// `usePipelining := false`
//   Grund fuer das Abschalten ist nicht ueberliefert. Erst ohne betreiben; falls
//   Pipelining hier tatsaechlich bricht, ist *das* der eigentliche Befund und
//   gehoert untersucht statt umschifft (AGENTS.md: keine Workarounds).
//
// `Global / concurrentRestrictions += Tags.limitAll(1)`
//   Serialisierte den kompletten Build ueber neun Module. Siehe CLAUDE_REVIEW_1.md P5-5.
//   Wenn der Build ohne diese Zeile bricht, bitte den echten Fehler notieren.
// ----------------------------------------------------------------------------

// Wieder auf den sbt-1-Wert gesetzt -- Ursache, nicht Geschmack:
//
// sbt 2 setzt `exportJars := true` per Default, ein Modul liegt fuer die
// abhaengigen Module also als JAR auf dem Classpath. Der sbt-Server haelt diese
// JARs offen (Zinc/Classloader). Unter Windows laesst sich eine offene Datei
// nicht per Rename ersetzen, und `packageBin` schreibt genau so: erst .tmp,
// dann `Files.move`. Ergebnis war reproduzierbar
//
//   java.nio.file.AccessDeniedException:
//     ...\scalajs-jfx-core_sjs1_3-3.0.0-SNAPSHOT.jar.151b4332.tmp
//       -> ...\scalajs-jfx-core_sjs1_3-3.0.0-SNAPSHOT.jar
//
// bei *jedem* Lauf nach dem ersten im selben Server -- auch ohne Quelltext-
// aenderung, weil packageBin jedes Mal laeuft. Nur ein Serverneustart half.
// Mit Klassenverzeichnissen statt JARs entfaellt das Problem; drei
// aufeinanderfolgende `sbtn test` im selben Server laufen gruen.
//
// Entfaellt, sobald packageBin unter Windows ohne Rename auf eine offene Datei
// auskommt oder der Server die Classpath-JARs wieder freigibt.
exportJars := false

lazy val commonJsSettings = Seq(
  scalaJSLinkerConfig := scalaJSLinkerConfig.value
    .withModuleKind(ModuleKind.ESModule)
    .withESFeatures(_.withESVersion(ESVersion.ES2021))
    .withSourceMap(true),
  // Die Sourcemap-Basis muss pro Link-Task auf dessen eigenes Ausgabeverzeichnis
  // zeigen. Vorher stand nur ein gemeinsamer Wert da, der auch fuer fullLinkJS
  // aufs fastopt-Verzeichnis zeigte. Siehe CLAUDE_REVIEW_1.md P5-5, Punkt 3.
  //
  // Die rechte Seite liest bewusst aus `fastOptJS` bzw. `fullOptJS`, nicht aus
  // dem blanken `scalaJSLinkerConfig`. Genau das war ein Fehler, der ein halbes
  // Jahr unbemerkt blieb (CLAUDE_REVIEW_3.md §2.0):
  //
  //   sbt-scalajs definiert `<stage>LinkJS / scalaJSLinkerConfig` als
  //   `(<stage>OptJS / scalaJSLinkerConfig).value` (ScalaJSPluginInternal.scala:208),
  //   und haengt an `fullOptJS / scalaJSLinkerConfig` ein
  //   `.withSemantics(_.optimized).withMinify(true).withCheckIR(true)` (ebd. :496).
  //   `scalaJSLinkerConfig.value` umgeht diese Delegation und liest den
  //   unskopierten Projektwert -- ohne optimierte Semantik, ohne Minifizierung.
  //   `fullLinkJS` lieferte dadurch ein zu `fastLinkJS` *byteidentisches* Bundle,
  //   fuer alle neun Module, inklusive `application`s viteFullLinkJS.
  //
  // Gemessen an scalajs-jfx-bridge: 1 705 389 -> 981 614 B roh, 217 700 ->
  // 155 380 B gzip. Wer diese Zeilen anfasst, prueft das mit einem md5-Vergleich
  // von fastopt/main.js und fullopt/main.js -- sind sie gleich, ist es wieder da.
  Compile / fastLinkJS / scalaJSLinkerConfig :=
    (Compile / fastOptJS / scalaJSLinkerConfig).value
      .withRelativizeSourceMapBase(
        Some((Compile / fastLinkJS / scalaJSLinkerOutputDirectory).value.toURI)
      ),
  Compile / fullLinkJS / scalaJSLinkerConfig :=
    (Compile / fullOptJS / scalaJSLinkerConfig).value
      .withRelativizeSourceMapBase(
        Some((Compile / fullLinkJS / scalaJSLinkerOutputDirectory).value.toURI)
      )
)

lazy val commonLibrarySettings = Seq(
  // Der Doc-Jar bleibt leer. Maven Central verlangt nur, dass das Artefakt
  // existiert, nicht dass Inhalt drin ist. Frueher lag hier ein Mapping, das die
  // README hineinkopierte — in sbt 2 ist `mappings` auf
  // `Seq[(xsbti.HashedVirtualFileRef, String)]` umgestellt, ein `java.io.File`
  // passt dort nicht mehr hinein. Falls die README wieder rein soll, geht das
  // ueber den FileConverter:
  //
  //   Compile / packageDoc / mappings += {
  //     val readme = (LocalRootProject / baseDirectory).value / "README.md"
  //     fileConverter.value.toVirtualFile(readme.toPath) -> "README.md"
  //   }
  Compile / doc / sources                := Seq.empty,
  libraryDependencies += "org.scala-js"  %% "scalajs-dom" % "2.8.1",
  libraryDependencies += "org.scalatest" %% "scalatest"   % "3.2.19" % Test
)

// Publish-Regel: Ein publiziertes Modul darf nur auf publizierte Module und externe
// Artefakte haengen. Sonst verweist der erzeugte POM auf ein Artefakt, das in Maven
// Central nie existiert, und das Modul ist fuer externe Konsumenten unaufloesbar.
// Publiziert: core, router, viewport, json, controls, forms, webauthn.
// Nicht publiziert (`publish / skip := true`): editor, demo.

lazy val jfxCore = Project(id = "scalajs-jfx-core", base = file("jfx-core"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name                                 := "scalajs-jfx-core",
    moduleName                           := "scalajs-jfx-core",
    libraryDependencies += "com.anjunar" %% "scala-reflect" % "1.1.3"
  )
  .settings(commonLibrarySettings)
  .settings(commonJsSettings)

lazy val jfxRouter = Project(id = "scalajs-jfx-router", base = file("jfx-router"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(jfxCore)
  .settings(
    name       := "scalajs-jfx-router",
    moduleName := "scalajs-jfx-router"
  )
  .settings(commonLibrarySettings)
  .settings(commonJsSettings)

lazy val jfxViewport = Project(id = "scalajs-jfx-viewport", base = file("jfx-viewport"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(jfxCore)
  .settings(
    name       := "scalajs-jfx-viewport",
    moduleName := "scalajs-jfx-viewport"
  )
  .settings(commonLibrarySettings)
  .settings(commonJsSettings)

lazy val jfxJson = Project(id = "scalajs-jfx-json", base = file("jfx-json"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(jfxCore)
  .settings(
    name                                 := "scalajs-jfx-json",
    moduleName                           := "scalajs-jfx-json",
    libraryDependencies += "com.anjunar" %% "scala-reflect" % "1.1.3"
  )
  .settings(commonLibrarySettings)
  .settings(commonJsSettings)

// The JavaScript boundary described in JAVASCRIPT_API.md. Depends on jfx-core alone for now --
// step 2 of §9 there ("nur core: Property, Scope, mount/hydrate/renderToString"). Router and forms
// facades are later steps in that same section, not missed dependencies here.
lazy val jfxBridge = Project(id = "scalajs-jfx-bridge", base = file("jfx-bridge"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(jfxCore)
  .settings(
    name       := "scalajs-jfx-bridge",
    moduleName := "scalajs-jfx-bridge",
    // "gelinktes ES-Modul" (JAVASCRIPT_API.md §7) -- linked straight into the npm package that
    // ships it, the same way `app`'s viteFullLinkJS lands in target/vite for vite to pick up.
    // fastLinkJS is what a TypeScript consumer's dev loop uses; fullLinkJS is step 4 of §9
    // ("Bundle-Größe messen"), not yet wired into a production build of its own.
    Compile / fastLinkJS / scalaJSLinkerOutputDirectory :=
      (LocalRootProject / baseDirectory).value / "npm" / "scalajs-jfx-bridge" / "dist" / "fastopt",
    Compile / fullLinkJS / scalaJSLinkerOutputDirectory :=
      (LocalRootProject / baseDirectory).value / "npm" / "scalajs-jfx-bridge" / "dist" / "fullopt"
  )
  .settings(commonLibrarySettings)
  .settings(commonJsSettings)

lazy val jfxControls = Project(id = "scalajs-jfx-controls", base = file("jfx-controls"))
  .enablePlugins(ScalaJSPlugin)
  // Kein jfxRouter: eine generische Tabelle darf nicht wissen, dass es Routing
  // gibt. Den aktuellen Pfad liefert jfx.core.context.CrawlScope, den der Router
  // in seiner compose bereitstellt. Siehe CLAUDE_REVIEW_1.md P1-4.
  .dependsOn(jfxCore, jfxViewport % "test->compile")
  .settings(
    name       := "scalajs-jfx-controls",
    moduleName := "scalajs-jfx-controls"
  )
  .settings(commonLibrarySettings)
  .settings(commonJsSettings)

lazy val jfxForms = Project(id = "scalajs-jfx-forms", base = file("jfx-forms"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(jfxCore, jfxControls, jfxViewport)
  .settings(
    name                                       := "scalajs-jfx-forms",
    moduleName                                 := "scalajs-jfx-forms",
    libraryDependencies += "io.github.cquiroz" %% "scala-java-time" % "2.6.0"
  )
  .settings(commonLibrarySettings)
  .settings(commonJsSettings)

lazy val jfxEditor = Project(id = "scalajs-jfx-editor", base = file("jfx-editor"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(jfxForms)
  .settings(
    name                                 := "scalajs-jfx-editor",
    moduleName                           := "scalajs-jfx-editor",
    libraryDependencies += "com.anjunar" %% "scalajs-lexical" % "1.3.0",
    publish / skip                       := true
  )
  .settings(commonLibrarySettings)
  .settings(commonJsSettings)

lazy val jfxWebAuthn = Project(id = "scalajs-jfx-webauthn", base = file("jfx-webAuthn"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name       := "scalajs-jfx-webauthn",
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
    // Die Integrationsschicht — SSR, Router, i18n, Theme — hatte keine Tests. Siehe CLAUDE_REVIEW_1.md P5-6.
    // Nur die Test-Abhaengigkeit, nicht `commonLibrarySettings`: das Demo-Modul wird nicht
    // publiziert und braucht weder Doc-Jar-Regeln noch eine eigene scalajs-dom-Zeile.
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % Test,
    // site.config.json ist die einzige Quelle fuer Deploy-Pfad und Site-Metadaten.
    // Sie speist sitemap.xml/robots.txt (tools/) und ueber diesen Generator den
    // Scala-Code, der das vollstaendige Dokument inklusive Head rendert.
    Compile / sourceGenerators += Def.task {
      SiteConfigGenerator(
        (LocalRootProject / baseDirectory).value / "site.config.json",
        (Compile / sourceManaged).value
      )
    }.taskValue,
    // Def.uncached: `Attributed[Report]` hat keinen JsonFormat, und der Task
    // schreibt ausserdem am Dateisystem. Ohne die Huelle scheitert sbt 2 beim
    // Laden des Builds.
    viteFullLinkJS := Def.uncached {
      val linked = (Compile / fullLinkJS).value
      sanitizeScalaJsSourceMap(
        (LocalRootProject / baseDirectory).value,
        (Compile / fullLinkJS / scalaJSLinkerOutputDirectory).value,
        streams.value.log
      )
      linked
    },
    // sbt 2 vereinheitlicht `target/` auf ein Verzeichnis in der Build-Wurzel.
    // Diese beiden expliziten Ueberschreibungen halten die Linker-Ausgabe dort,
    // wo vite.config.js sie erwartet — jetzt umso wichtiger.
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
    jfxBridge,
    jfxControls,
    jfxForms,
    jfxEditor,
    jfxWebAuthn,
    app
  )
  .settings(
    publish / skip := true
  )
