package jfx.core.document

import jfx.core.render.SsrHostElement
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** How the head reaches the wire.
  *
  * Escaping is the part worth pinning down: a title is character data and has to be escaped, a
  * script's body is raw text and must not be -- escaping `&` inside JSON-LD would corrupt the
  * payload rather than protect it, and nothing downstream would report it.
  */
class SsrHeadSinkSpec extends AnyFlatSpec with Matchers {

  private def render(entries: HeadEntry*): String = {
    val head = new SsrHostElement("head")
    new SsrHeadSink(head, None).update(entries, Nil)
    head.renderHtml()
  }

  "A rendered head" should "mark every entry with its key" in {
    render(HeadEntry.title("Router")) shouldBe
      """<head><title data-jfx-head="title">Router</title></head>"""
  }

  it should "write void elements without a closing tag" in {
    render(HeadEntry.charset()) shouldBe
      """<head><meta data-jfx-head="meta:charset" charset="UTF-8"></head>"""
  }

  it should "escape character data in a title" in {
    render(HeadEntry.title("Forms & <Controls>")) should include(
      ">Forms &amp; &lt;Controls&gt;<"
    )
  }

  it should "leave the body of a script unescaped" in {
    val script = "if (a && b) { document.documentElement.dataset.theme = 'dark' }"

    render(HeadEntry.inlineScript("theme-init", script)) shouldBe
      s"""<head><script data-jfx-head="theme-init">$script</script></head>"""
  }

  it should "keep JSON-LD valid while closing its own script element" in {
    val html = render(HeadEntry.jsonLd("ld:site", """{"name":"a </script> trap"}"""))

    // The payload keeps its quotes and ampersands, but cannot end the element early.
    html should include("""{"name":"a <\/script> trap"}""")
    html.indexOf("</script>") shouldBe html.lastIndexOf("</script>")
  }

  it should "replace its previous content rather than appending to it" in {
    val head = new SsrHostElement("head")
    val sink = new SsrHeadSink(head, None)

    sink.update(Seq(HeadEntry.title("first")), Nil)
    sink.update(Seq(HeadEntry.title("second")), Nil)

    head.renderHtml() shouldBe
      """<head><title data-jfx-head="title">second</title></head>"""
  }

  "Html attributes" should "land on the html host and drop when they go" in {
    val head = new SsrHostElement("head")
    val html = new SsrHostElement("html")
    val sink = new SsrHeadSink(head, Some(html))

    sink.update(Nil, Seq("lang" -> "de"))
    html.attribute("lang") shouldBe Some("de")

    sink.update(Nil, Nil)
    html.attribute("lang") shouldBe None
  }
}
