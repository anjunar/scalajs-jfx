package jfx.bridge

import jfx.core.state.Property
import jfx.forms.Media
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.scalajs.js

class MediaCodecSpec extends AnyFlatSpec with Matchers {
  private def image(name: String): js.Dictionary[js.Any] =
    js.Dictionary(
      "id" -> "11111111-1111-4111-8111-111111111111",
      "name" -> name,
      "contentType" -> "image/png",
      "data" -> "data:image/png;base64,aGVsbG8="
    )

  "MediaCodec" should "initialize from the model without echoing a converted value" in {
    val initial = image("initial")
    val source = Property[js.Any](initial)
    val target = Property[Media](null)
    var writes = 0
    val observer = source.observeWithoutInitial(_ => writes += 1)
    val binding = MediaCodec.subscribeBidirectional(source, target)

    source.get shouldBe initial
    writes shouldBe 0
    target.get.name.get shouldBe "initial"
    target.get.id.get.toString shouldBe initial("id").asInstanceOf[String]

    val replacement = image("replacement")
    source.set(replacement)
    writes shouldBe 1
    target.get.name.get shouldBe "replacement"
    target.set(MediaCodec.fromJs(initial))
    writes shouldBe 2
    source.get.asInstanceOf[js.Dictionary[js.Any]]("name").asInstanceOf[String] shouldBe "initial"

    target.set(null)
    source.get shouldBe null
    source.set(replacement)
    binding.dispose()
    source.set(initial)
    target.get.name.get shouldBe "replacement"
    target.set(null)
    source.get shouldBe initial
    observer.dispose()
  }

  it should "preserve media and thumbnail IDs and accept an absent or null thumbnail" in {
    val original = image("full")
    original("thumbnail") = image("thumbnail")
    val roundTrip = MediaCodec.toJs(MediaCodec.fromJs(original)).asInstanceOf[js.Dictionary[js.Any]]
    roundTrip("id").asInstanceOf[String] shouldBe original("id").asInstanceOf[String]
    roundTrip("thumbnail").asInstanceOf[js.Dictionary[js.Any]]("id").asInstanceOf[String] shouldBe
      original("thumbnail").asInstanceOf[js.Dictionary[js.Any]]("id").asInstanceOf[String]
    MediaCodec.fromJs(image("absent")).thumbnail.get shouldBe null
    original("thumbnail") = null
    MediaCodec.fromJs(original).thumbnail.get shouldBe null
  }
}
