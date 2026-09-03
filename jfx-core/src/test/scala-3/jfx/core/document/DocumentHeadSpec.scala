package jfx.core.document

import scala.collection.mutable

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** The registry rules the rendered output cannot show.
  *
  * `AppSsrSpec` proves that a route's metadata reaches the served HTML. What it cannot prove is why
  * that keeps working once two components describe the same thing: the site registers a
  * description, a page overrides it, and when the page goes away the site's has to come back. That
  * is the whole point of the stack, and it is invisible in a single render.
  */
class DocumentHeadSpec extends AnyFlatSpec with Matchers {

  private def contentOf(head: DocumentHead, key: String): Option[String] =
    head.entries.find(_.key == key).flatMap(_.attributes.toMap.get("content"))

  "A later registration" should "override an earlier one for the same key" in {
    val head = new DocumentHead

    head.push(HeadEntry.meta("description", "the site"))
    head.push(HeadEntry.meta("description", "the page"))

    contentOf(head, "meta:name=description") shouldBe Some("the page")
    head.entries.count(_.key == "meta:name=description") shouldBe 1
  }

  it should "uncover the earlier one again when it is disposed" in {
    val head = new DocumentHead

    head.push(HeadEntry.meta("description", "the site"))
    val page = head.push(HeadEntry.meta("description", "the page"))

    page.dispose()

    contentOf(head, "meta:name=description") shouldBe Some("the site")
  }

  it should "leave the key out entirely once nothing holds it" in {
    val head = new DocumentHead

    val only = head.push(HeadEntry.meta("robots", "noindex"))
    only.dispose()

    head.entries.map(_.key) should not contain "meta:name=robots"
  }

  "A key's position" should "survive being registered again" in {
    val head = new DocumentHead

    head.push(HeadEntry.charset())
    val title = head.push(HeadEntry.title("first"))
    head.push(HeadEntry.link("canonical", "https://example.org/"))

    // What a Handle does on every navigation: drop the old registration, add a new one.
    title.dispose()
    head.push(HeadEntry.title("second"))

    head.entries.map(_.key) shouldBe Seq("meta:charset", "title", "link:canonical")
    head.entries.find(_.key == "title").flatMap(_.text) shouldBe Some("second")
  }

  "A handle" should "replace its own entries and nothing else" in {
    val head = new DocumentHead

    head.push(HeadEntry.charset())

    val page = head.handle()
    page.set(HeadEntry.title("Router"), HeadEntry.meta("description", "paths"))
    page.set(HeadEntry.title("Button"))

    head.entries.map(_.key) shouldBe Seq("meta:charset", "title")
    head.entries.find(_.key == "title").flatMap(_.text) shouldBe Some("Button")
  }

  it should "give the site default back when it is disposed" in {
    val head = new DocumentHead

    head.push(HeadEntry.meta("description", "the site"))

    val page = head.handle()
    page.set(HeadEntry.meta("description", "the page"))
    page.dispose()

    contentOf(head, "meta:name=description") shouldBe Some("the site")
  }

  "A batch" should "write to the sink once instead of once per entry" in {
    val head = new DocumentHead
    val sink = new RecordingHeadSink

    head.connect(sink)
    sink.updates shouldBe 1

    head.batch {
      head.push(HeadEntry.charset())
      head.push(HeadEntry.title("Router"))
      head.push(HeadEntry.meta("description", "paths"))
    }

    sink.updates shouldBe 2
    sink.lastEntries.map(_.key) shouldBe Seq("meta:charset", "title", "meta:name=description")
  }

  "A sink that connects late" should "receive everything registered before it" in {
    val head = new DocumentHead
    val sink = new RecordingHeadSink

    head.push(HeadEntry.title("Router"))
    head.htmlAttribute("lang", "de")

    head.connect(sink)

    sink.lastEntries.map(_.key) shouldBe Seq("title")
    sink.lastHtmlAttributes shouldBe Seq("lang" -> "de")
  }

  "An html attribute" should "keep the last value written and disappear when removed" in {
    val head = new DocumentHead

    head.htmlAttribute("lang", "en")
    head.htmlAttribute("lang", "de")

    head.htmlAttributes shouldBe Seq("lang" -> "de")

    head.removeHtmlAttribute("lang")
    head.htmlAttributes shouldBe empty
  }
}

private final class RecordingHeadSink extends HeadSink {
  private val entriesSeen    = mutable.ArrayBuffer.empty[Seq[HeadEntry]]
  private val attributesSeen = mutable.ArrayBuffer.empty[Seq[(String, String)]]

  def update(entries: Seq[HeadEntry], htmlAttributes: Seq[(String, String)]): Unit = {
    entriesSeen += entries
    attributesSeen += htmlAttributes
  }

  def updates: Int = entriesSeen.length

  def lastEntries: Seq[HeadEntry] = entriesSeen.last

  def lastHtmlAttributes: Seq[(String, String)] = attributesSeen.last
}
