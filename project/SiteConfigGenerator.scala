import sbt._

/**
 * Erzeugt `app.SiteConfig` aus `site.config.json`.
 *
 * Der Deploy-Pfad darf nicht doppelt gepflegt werden. `site.config.json` speist
 * index.html (Vite-Plugin), sitemap.xml/robots.txt (tools/) und ueber diesen
 * Generator den Scala-Code. Es ist bewusst ein Generator und keine Laufzeit-
 * Erkennung: SSR hat kein DOM, koennte also kein `<base href>` lesen, und ein
 * abweichender basePath zwischen Server und Browser bricht die Hydration.
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
   * Minimaler Parser fuer die flachen String-Felder von site.config.json.
   * Arrays und verschachtelte Objekte werden ignoriert -- sie werden auf der
   * Scala-Seite nicht gebraucht, und eine JSON-Bibliothek in der Build-Definition
   * waere fuer diesen Zweck unverhaeltnismaessig.
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
