package jfx.core.render

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** `<meta …></meta>` used to go out for every void element. A browser repairs that while parsing,
  * so hydration never noticed -- but a prerender diff, a crawler with a strict parser or an XML
  * tool sees a different tree than the browser does. With the head rendered from Scala, `meta` and
  * `link` made it the common case. See REVIEW.md C-7.
  *
  * `ImageSpec` covers the same rule for a component; here it is the serializer itself, including
  * the case no component can produce.
  */
class VoidElementSpec extends AnyFlatSpec with Matchers {

  "A void element" should "be written without a closing tag, attributes and all" in {
    val element = new SsrHostElement("link")
    element.setAttribute("rel", "canonical")
    element.setAttribute("href", "https://example.org/")

    element.renderHtml() shouldBe """<link rel="canonical" href="https://example.org/">"""
  }

  it should "report children instead of dropping them" in {
    val element = new SsrHostElement("meta")
    element.insertChild(0, new SsrTextNode("nope"))

    val failure = the[IllegalStateException] thrownBy element.renderHtml()
    failure.getMessage should include("<meta>")
    failure.getMessage should include("void element")
  }

  it should "be recognised regardless of how the tag is spelled" in {
    VoidElements.contains("BR") shouldBe true
    VoidElements.contains("div") shouldBe false
  }

  "A normal element" should "keep its closing tag when it is empty" in {
    new SsrHostElement("div").renderHtml() shouldBe "<div></div>"
  }
}
