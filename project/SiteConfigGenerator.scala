import sbt._

/**
 * Generates `app.SiteConfig` from `site.config.json`.
 *
 * The deployment path must not be maintained twice. `site.config.json` feeds
 * index.html (Vite plugin), sitemap.xml/robots.txt (tools/), and Scala code through this generator.
 * This is deliberately a generator rather than runtime detection: SSR has no DOM and therefore
 * cannot read a `<base href>`, while a differing basePath between server and browser breaks hydration.
 */
object SiteConfigGenerator {

  def apply(configFile: File, sourceManagedDir: File): Seq[File] = {
    val values = parse(IO.read(configFile))

    def required(key: String): String =
      values.getOrElse(
        key,
        sys.error(s"${configFile.getName} braucht den Schluessel \"$key\".")
      )

    val basePath = normalizeBasePath(required("basePath"))
    val siteUrl  = required("siteUrl").stripSuffix("/")

    val target = sourceManagedDir / "app" / "SiteConfig.scala"

    val content =
      s"""package app
         |
         |// GENERIERT aus site.config.json -- nicht von Hand aendern.
         |// Quelle: SiteConfigGenerator (project/SiteConfigGenerator.scala)
         |object SiteConfig {
         |
         |  /** Deploy-Pfad mit fuehrendem, ohne abschliessenden Slash. Root-Deploy ist "". */
         |  val basePath: String = "${escape(basePath)}"
         |
         |  /** Absolute Basis-URL des Deploys, ohne abschliessenden Slash. */
         |  val siteUrl: String = "${escape(siteUrl)}"
         |
         |  val name: String = "${escape(required("name"))}"
         |
         |  val title: String = "${escape(required("title"))}"
         |
         |  val description: String = "${escape(required("description"))}"
         |
         |  /** localStorage-Schluessel des Themes -- teilt sich den Wert mit dem
         |    * Inline-Skript in index.html. */
         |  val themeStorageKey: String = "${escape(required("themeStorageKey"))}"
         |}
         |""".stripMargin

    if (!target.exists() || IO.read(target) != content) {
      IO.write(target, content)
    }

    Seq(target)
  }

  private def normalizeBasePath(value: String): String =
    if (value == null || value.isEmpty || value == "/") ""
    else {
      val withLeadingSlash = if (value.startsWith("/")) value else s"/$value"
      withLeadingSlash.stripSuffix("/")
    }

  private def escape(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")

  /**
   * Minimal parser for the flat string fields in site.config.json. Arrays and nested objects are
   * ignored -- Scala does not need them, and a JSON library in the build definition would be
   * disproportionate for this purpose.
   */
  private def parse(json: String): Map[String, String] = {
    val entry = """"([A-Za-z0-9_]+)"\s*:\s*"((?:[^"\\]|\\.)*)"""".r
    entry
      .findAllMatchIn(json)
      .map(m => m.group(1) -> unescape(m.group(2)))
      .toMap
  }

  private def unescape(value: String): String =
    value.replace("\\\"", "\"").replace("\\\\", "\\")
}
