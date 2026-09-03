package jfx.core.render

/** The HTML elements that are written without a closing tag.
  *
  * [[SsrHostElement]] used to emit `<meta …></meta>` for all of them. A browser repairs that while
  * parsing, so hydration never noticed -- but everything that reads the SSR string with a strict
  * parser (prerender diffs, crawlers, XML tooling) then sees a different tree than the browser.
  * With the document head rendered from Scala, `meta` and `link` became the common case. See
  * REVIEW.md C-7.
  */
object VoidElements {

  private val names: Set[String] =
    Set(
      "area",
      "base",
      "br",
      "col",
      "embed",
      "hr",
      "img",
      "input",
      "link",
      "meta",
      "param",
      "source",
      "track",
      "wbr"
    )

  def contains(tagName: String): Boolean =
    names.contains(tagName.toLowerCase)
}
