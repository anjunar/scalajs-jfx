import org.scalajs.linker.interface.{ESVersion, ModuleKind}
import org.scalajs.sbtplugin.ScalaJSPlugin
import sbt.url

val siteConfigBasePathOverride = settingKey[Option[String]](
  "Optional build-time base path override for the generated Scala site config."
)
val siteConfigUrlOverride = settingKey[Option[String]](
  "Optional build-time site URL override for the generated Scala site config."
)

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
// 3. Slash-Syntax ist Pflicht, 0.13-Syntax ist entfernt. War hier schon so.
// ---------------------------------------------------------------------------

version              := "1.0.0"
organization         := "com.anjunar"
organizationName     := "Anjunar"
organizationHomepage := Some(url("https://github.com/anjunar"))

scalaVersion := "3.3.8"

homepage := Some(url("https://github.com/anjunar/scalajs-ui"))
description := "Reactive UI framework for Scala.js with lifecycle control, typed forms, routing, tables, and a composable DSL."

licenses := Seq("MIT" -> url("https://opensource.org/licenses/MIT"))

scmInfo := Some(
  ScmInfo(
    url("https://github.com/anjunar/scalajs-ui"),
    "scm:git:https://github.com/anjunar/scalajs-ui.git",
    Some("scm:git:git@github.com:anjunar/scalajs-ui.git")
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
//     ...\scalajs-ui-core_sjs1_3-1.0.0.jar.151b4332.tmp
//       -> ...\scalajs-ui-core_sjs1_3-1.0.0.jar
//
// bei *jedem* Lauf nach dem ersten im selben Server -- auch ohne Quelltext-
// aenderung, weil packageBin jedes Mal laeuft. Nur ein Serverneustart half.
// Mit Klassenverzeichnissen statt JARs entfaellt das Problem; drei
// aufeinanderfolgende `sbt --server test` im selben Server laufen gruen.
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
  //   fuer alle neun Module, inklusive scalajs-ui-demo.
  //
  // Gemessen an scalajs-ui-bridge: 1 705 389 -> 981 614 B roh, 217 700 ->
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
// Publiziert: core, router, viewport, json, controls, forms, editor, webauthn.
// Nicht publiziert (`publish / skip := true`): demo.
// FINAL.md Prioritaet 4 ("scalajs-ui-editor veroeffentlichen oder bewusst ausklammern")
// ist damit entschieden: veroeffentlichen, mit einer @anjunar/scalajs-ui-editor-Fassade
// wie jedes andere npm/scalajs-ui-*-Paket (npm-Modularisierung, Lauf 7).

lazy val uiCore = Project(id = "scalajs-ui-core", base = file("scala/scalajs-ui-core"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name                                 := "scalajs-ui-core",
    moduleName                           := "scalajs-ui-core",
    libraryDependencies += "com.anjunar" %% "scala-reflect" % "1.1.3"
  )
  .settings(commonLibrarySettings)
  .settings(commonJsSettings)

lazy val uiRouter = Project(id = "scalajs-ui-router", base = file("scala/scalajs-ui-router"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(uiCore)
  .settings(
    name       := "scalajs-ui-router",
    moduleName := "scalajs-ui-router"
  )
  .settings(commonLibrarySettings)
  .settings(commonJsSettings)

lazy val uiViewport = Project(id = "scalajs-ui-viewport", base = file("scala/scalajs-ui-viewport"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(uiCore)
  .settings(
    name       := "scalajs-ui-viewport",
    moduleName := "scalajs-ui-viewport"
  )
  .settings(commonLibrarySettings)
  .settings(commonJsSettings)

lazy val uiJson = Project(id = "scalajs-ui-json", base = file("scala/scalajs-ui-json"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(uiCore)
  .settings(
    name                                 := "scalajs-ui-json",
    moduleName                           := "scalajs-ui-json",
    libraryDependencies += "com.anjunar" %% "scala-reflect" % "1.1.3"
  )
  .settings(commonLibrarySettings)
  .settings(commonJsSettings)

// The JavaScript boundary described in JAVASCRIPT_API.md. Depends on scalajs-ui-core, scalajs-ui-router,
// scalajs-ui-controls and scalajs-ui-viewport: step 5 of §9 there wires the router facade (`router`, `router-outlet`,
// `router-link`) into the registry, step 6 the controls facade (`tabs`, `carousel`, `table-view`,
// `data-grid`, `virtual-list-view`), step 7 the viewport facade (`viewport`, `window`, `overlay`,
// `notification`). A wider `dependsOn` edge costs zero bytes on its own (CLAUDE_REVIEW_3.md §2.1,
// E1==E2); what the linked bundle pays for is the *registration* in BridgeRuntime, measured in §14.
//
// Controls also uses Viewport in production for the optional TableView column menu.
// The bridge retains its explicit edge because it registers viewport factories itself.
lazy val uiBridge = Project(id = "scalajs-ui-bridge", base = file("scala/scalajs-ui-bridge"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(uiCore, uiRouter, uiControls, uiViewport, uiForms, uiEditor)
  .settings(
    name       := "scalajs-ui-bridge",
    moduleName := "scalajs-ui-bridge",
    // "gelinktes ES-Modul" (JAVASCRIPT_API.md §7) -- linked straight into the npm package that
    // ships it, the same way the app's fullLinkJS lands in target/vite for Vite to pick up.
    // fastLinkJS is what a TypeScript consumer's dev loop uses; fullLinkJS is step 4 of §9
    // ("Bundle-Größe messen"), not yet wired into a production build of its own.
    Compile / fastLinkJS / scalaJSLinkerOutputDirectory :=
      (LocalRootProject / baseDirectory).value / "npm" / "scalajs-ui-bridge" / "dist" / "fastopt",
    Compile / fullLinkJS / scalaJSLinkerOutputDirectory :=
      (LocalRootProject / baseDirectory).value / "npm" / "scalajs-ui-bridge" / "dist" / "fullopt"
  )
  .settings(commonLibrarySettings)
  .settings(commonJsSettings)
  .settings(
    // The Scala.js linker map contains sources from the complete multi-project
    // build (including external libraries). Those sources are not part of the
    // published bridge package, so shipping the map creates misleading source
    // paths for npm consumers. Keep the npm runtime artifact self-consistent:
    // the TypeScript facades retain embedded source maps, while this generated
    // bridge bundle deliberately has no unusable external source map.
    Compile / fastLinkJS / scalaJSLinkerConfig ~= (_.withSourceMap(false)),
    Compile / fullLinkJS / scalaJSLinkerConfig ~= (_.withSourceMap(false))
  )

lazy val uiControls = Project(id = "scalajs-ui-controls", base = file("scala/scalajs-ui-controls"))
  .enablePlugins(ScalaJSPlugin)
  // Kein uiRouter: eine generische Tabelle darf nicht wissen, dass es Routing
  // gibt. Den aktuellen Pfad liefert ui.core.context.CrawlScope, den der Router
  // in seiner compose bereitstellt. Siehe CLAUDE_REVIEW_1.md P1-4.
  .dependsOn(uiCore, uiViewport)
  .settings(
    name       := "scalajs-ui-controls",
    moduleName := "scalajs-ui-controls"
  )
  .settings(commonLibrarySettings)
  .settings(commonJsSettings)

lazy val uiForms = Project(id = "scalajs-ui-forms", base = file("scala/scalajs-ui-forms"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(uiCore, uiControls, uiViewport)
  .settings(
    name                                       := "scalajs-ui-forms",
    moduleName                                 := "scalajs-ui-forms",
    libraryDependencies += "io.github.cquiroz" %% "scala-java-time" % "2.6.0"
  )
  .settings(commonLibrarySettings)
  .settings(commonJsSettings)

lazy val uiEditor = Project(id = "scalajs-ui-editor", base = file("scala/scalajs-ui-editor"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(uiForms)
  .settings(
    name                                 := "scalajs-ui-editor",
    moduleName                           := "scalajs-ui-editor",
    libraryDependencies += "com.anjunar" %% "scalajs-lexical" % "1.4.0"
  )
  .settings(commonLibrarySettings)
  .settings(commonJsSettings)

lazy val uiWebAuthn = Project(id = "scalajs-ui-webauthn", base = file("scala/scalajs-ui-webauthn"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name       := "scalajs-ui-webauthn",
    moduleName := "scalajs-ui-webauthn"
  )
  .settings(commonLibrarySettings)
  .settings(commonJsSettings)

lazy val app = Project(id = "scalajs-ui-demo", base = file("scala/scalajs-ui-demo"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(
    uiCore,
    uiRouter,
    uiViewport,
    uiJson,
    uiControls,
    uiForms,
    uiEditor,
    uiWebAuthn
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
    siteConfigBasePathOverride := sys.env.get("UI_BASE_PATH"),
    siteConfigUrlOverride := sys.env.get("UI_SITE_URL"),
    Compile / sourceGenerators += Def.task {
      SiteConfigGenerator(
        (LocalRootProject / baseDirectory).value / "site.config.json",
        (Compile / sourceManaged).value,
        siteConfigBasePathOverride.value,
        siteConfigUrlOverride.value
      )
    }.taskValue,
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

// Isolated test application: exercises the public Scala core API in real browsers. Never published
// or linked into the production bridge; no editor implementation belongs to this repository.
lazy val uiCoreBrowserTests = Project(id = "scalajs-ui-core-browser-tests", base = file("scala/scalajs-ui-core-browser-tests"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(uiCore)
  .settings(commonJsSettings)
  .settings(
    publish / skip := true,
    Compile / fullLinkJS / scalaJSLinkerOutputDirectory :=
      (LocalRootProject / baseDirectory).value / "target" / "core-browser-tests"
  )

lazy val root = Project(id = "scalajs-ui-root", base = file("."))
  .aggregate(
    uiCore,
    uiCoreBrowserTests,
    uiRouter,
    uiViewport,
    uiJson,
    uiBridge,
    uiControls,
    uiForms,
    uiEditor,
    uiWebAuthn,
    app
  )
  .settings(
    publish / skip := true
  )
