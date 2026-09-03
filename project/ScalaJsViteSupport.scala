import sbt._
import sbt.Keys._

import scala.sys.process._

object ScalaJsViteSupport {

  val viteFullLinkJS = taskKey[Attributed[org.scalajs.linker.interface.Report]](
    "Links the demo for Vite production and sanitizes the generated sourcemap."
  )

  // Ursache: Scala.js relativiert die Sourcemap-Pfade gegen das Ausgabeverzeichnis.
  // Fuer die eigene Standardbibliothek ersetzt der Linker die Pfade vorher durch
  // raw.githubusercontent.com-URLs, die bleiben also aufloesbar. Abhaengigkeiten,
  // die *keine* Source-URI-Zuordnung publizieren, liefern dagegen die absoluten
  // Pfade ihrer Buildmaschine aus (`/home/runner/work/...`,
  // `/localhome/doeraene/...`). Relativiert werden daraus `../../../..`-Pfade auf
  // Verzeichnisse, die es hier nie gab — der Browser zeigt in diesen Frames
  // keinen Quelltext. Betroffen sind aktuell zwoelf Dateien aus
  // scala-java-locales, sbt-locales und portable-scala-reflect.
  //
  // Loesung: den fehlenden Quelltext aus dem `-sources.jar` im Coursier-Cache
  // lesen und als `sourcesContent` in die Map schreiben. Der Pfad bleibt stehen,
  // der Browser braucht ihn dann nicht mehr aufzuloesen.
  //
  // Entfaellt, sobald diese Abhaengigkeiten ihre Sourcemaps mit
  // `-scalajs-mapSourceURI` publizieren oder wir sie nicht mehr benutzen.
  def sanitizeScalaJsSourceMap(
      repoRoot: File,
      outputDirectory: File,
      log: Logger
  ): Unit = {
    val sourceMapFile = outputDirectory / "main.js.map"

    if (!sourceMapFile.exists()) {
      log.warn(
        s"Skipping sourcemap sanitization because ${sourceMapFile.getAbsolutePath} is missing."
      )
    } else {
      val scriptFile = repoRoot / "tools" / "sanitize-scalajs-sourcemap.mjs"
      val exitCode   = Process(
        Seq("node", scriptFile.getAbsolutePath, sourceMapFile.getAbsolutePath),
        repoRoot
      ).!(ProcessLogger(log.info(_), log.warn(_)))

      if (exitCode != 0) {
        sys.error(s"Sourcemap sanitization failed for ${sourceMapFile.getAbsolutePath}.")
      }
    }
  }
}
