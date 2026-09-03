package jfx.core.document

/** One element in the document head.
  *
  * [[key]] decides identity, not position: pushing the same key again replaces the entry, so a page
  * that sets `meta:name=description` overrides the site-wide default instead of adding a second
  * description. The factories below derive the key from whatever makes the tag unique -- the `name`
  * of a meta, the `rel` of a link -- and anything they do not cover builds a [[HeadEntry]]
  * directly with a key of its own choosing.
  *
  * @param text
  *   the element's character data, if it has any (`<title>`, an inline script)
  * @param rawText
  *   whether [[text]] reaches the output unescaped. True only for `<script>` and `<style>`, whose
  *   content HTML defines as raw text -- escaping it would corrupt the payload.
  */
final case class HeadEntry(
    key: String,
    tagName: String,
    attributes: Seq[(String, String)] = Nil,
    text: Option[String] = None,
    rawText: Boolean = false
)

object HeadEntry {

  def title(value: String): HeadEntry =
    HeadEntry("title", "title", Nil, Some(value))

  def charset(value: String = "UTF-8"): HeadEntry =
    HeadEntry("meta:charset", "meta", Seq("charset" -> value))

  def base(href: String): HeadEntry =
    HeadEntry("base", "base", Seq("href" -> href))

  def meta(name: String, content: String): HeadEntry =
    HeadEntry(s"meta:name=$name", "meta", Seq("name" -> name, "content" -> content))

  /** `<meta property="…">` -- the Open Graph form, which uses `property` rather than `name`. */
  def property(property: String, content: String): HeadEntry =
    HeadEntry(s"meta:property=$property", "meta", Seq("property" -> property, "content" -> content))

  def link(rel: String, href: String, attributes: (String, String)*): HeadEntry =
    HeadEntry(s"link:$rel", "link", Seq("rel" -> rel, "href" -> href) ++ attributes)

  /** `<link rel="alternate" hreflang="…">` -- keyed by language, because a document has one per
    * translation and they must not replace each other.
    */
  def alternate(hreflang: String, href: String): HeadEntry =
    HeadEntry(
      s"link:alternate:$hreflang",
      "link",
      Seq("rel" -> "alternate", "hreflang" -> hreflang, "href" -> href)
    )

  def script(key: String, src: String, attributes: (String, String)*): HeadEntry =
    HeadEntry(key, "script", Seq("src" -> src) ++ attributes)

  def inlineScript(key: String, source: String): HeadEntry =
    HeadEntry(key, "script", Nil, Some(source), rawText = true)

  def jsonLd(key: String, json: String): HeadEntry =
    HeadEntry(
      key,
      "script",
      Seq("type" -> "application/ld+json"),
      Some(escapeClosingTag(json)),
      rawText = true
    )

  /** Raw text ends at the first `</script`, wherever it appears -- inside a JSON string as well.
    * `<\/` is the escape JSON itself allows, so the payload stays valid and the element stays
    * closed where it should be.
    */
  private def escapeClosingTag(value: String): String =
    value.replace("</", "<\\/")
}
