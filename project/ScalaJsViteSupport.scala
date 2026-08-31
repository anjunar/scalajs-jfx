import sbt._
import sbt.Keys._

import scala.sys.process._

object ScalaJsViteSupport {

  val viteFastLinkJS = taskKey[Attributed[org.scalajs.linker.interface.Report]](
    "Links the demo for Vite dev and sanitizes the generated sourcemap."
  )

  val viteFullLinkJS = taskKey[Attributed[org.scalajs.linker.interface.Report]](
    "Links the demo for Vite production and sanitizes the generated sourcemap."
  )

  def sanitizeScalaJsSourceMap(
      repoRoot: File,
      outputDirectory: File,
      log: Logger
  ): Unit = {
    val sourceMapFile = outputDirectory / "main.js.map"

    if (!sourceMapFile.exists()) {
      log.warn(s"Skipping sourcemap sanitization because ${sourceMapFile.getAbsolutePath} is missing.")
    } else {
      val scriptFile = repoRoot / "tools" / "sanitize-scalajs-sourcemap.mjs"
      val exitCode = Process(
        Seq("node", scriptFile.getAbsolutePath, sourceMapFile.getAbsolutePath),
        repoRoot
      ).!(ProcessLogger(log.info(_), log.warn(_)))

      if (exitCode != 0) {
        sys.error(s"Sourcemap sanitization failed for ${sourceMapFile.getAbsolutePath}.")
      }
    }
  }
}
