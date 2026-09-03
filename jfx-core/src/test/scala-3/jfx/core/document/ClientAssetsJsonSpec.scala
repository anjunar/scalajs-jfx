package jfx.core.document

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** The one seam where the bundler reaches into the document.
  *
  * The file names carry a build-time content hash, so they arrive as an argument to the render.
  * Nothing downstream validates them, which is why a malformed list has to fail here rather than
  * produce a document with a silently missing script.
  */
class ClientAssetsJsonSpec extends AnyFlatSpec with Matchers {

  "Client assets" should "become head entries keyed by position" in {
    val entries = ClientAssetsJson.parse(
      """[{"tag":"link","attributes":{"rel":"stylesheet","href":"/assets/app.css"}},
        | {"tag":"script","attributes":{"type":"module","src":"/assets/app.js"}}]""".stripMargin
    )

    entries.map(_.key) shouldBe Seq("asset:0", "asset:1")
    entries.map(_.tagName) shouldBe Seq("link", "script")
    entries.head.attributes.toMap shouldBe Map(
      "rel"  -> "stylesheet",
      "href" -> "/assets/app.css"
    )
  }

  it should "accept an absent list" in {
    ClientAssetsJson.parse(null) shouldBe empty
    ClientAssetsJson.parse("") shouldBe empty
    ClientAssetsJson.parse("[]") shouldBe empty
  }

  it should "refuse anything that is not a list" in {
    an[IllegalArgumentException] should be thrownBy
      ClientAssetsJson.parse("""{"tag":"script"}""")
  }

  it should "refuse an entry without a tag" in {
    an[IllegalArgumentException] should be thrownBy
      ClientAssetsJson.parse("""[{"attributes":{"src":"/assets/app.js"}}]""")
  }
}
