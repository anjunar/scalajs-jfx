package ui.editor

import lexical.media.{ImageReference, InternalImageUrl}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

final class MarkdownImagesSpec extends AnyFlatSpec with Matchers {
  private val policy = MediaUrlPolicy.internal

  "Image Markdown" should "retain an explicit width including the old default and an optional title" in {
    Seq(1, 320, 680, Int.MaxValue).foreach { width =>
      val value    = ImageReference("/media/cat.webp", "Katze", Some("Im Garten"), Some(width))
      val markdown = s"""![Katze](/media/cat.webp "Im Garten"){width=$width}"""
      MarkdownImages.format(value, policy) shouldBe markdown
      MarkdownImages.parse(markdown, policy) shouldBe Some(value)
    }
  }

  it should "retain the absence of a width" in {
    val markdown = "![Katze](/media/cat.jpg)"
    MarkdownImages.parse(markdown, policy).get.widthPx shouldBe None
    MarkdownImages.format(MarkdownImages.parse(markdown, policy).get, policy) shouldBe markdown
  }

  it should "round trip escaped alt text, titles and URL parentheses" in {
    val value =
      ImageReference("/media/cat(1).webp", "Katze [1] \\ Garten", Some("A \"title\""), Some(320))
    MarkdownImages.parse(MarkdownImages.format(value, policy), policy) shouldBe Some(value)
  }

  it should "reject invalid widths without inventing a default" in {
    Seq("0", "-1", "1.5", "100%", "320px", "2147483648", "", "NaN").foreach { width =>
      val markdown = s"![Cat](/media/cat.jpg){width=$width}"
      MarkdownImages.parse(markdown, policy) shouldBe None
      MarkdownImages.validationError(markdown, policy).isDefined shouldBe true
    }
  }

  "Internal URLs" should "reject hosts, embedded bytes and encoded path ambiguity" in {
    Seq(
      "https://host/image.png",
      "http://host/image.png",
      "//host/image.png",
      "data:image/png;base64,YQ==",
      "blob:source",
      "image.png",
      "/\\host/a",
      "/media/../a",
      "/media/%2e%2e/a",
      "/%2fhost/a",
      "/media/%252e%252e/a",
      "/media/%5ca",
      "/media/a\n",
      "/media/%00",
      "/media/%ZZ"
    ).foreach { value =>
      withClue(value) { InternalImageUrl.valid(value) shouldBe false }
    }
    InternalImageUrl.valid("/media/cat.webp") shouldBe true
    InternalImageUrl.valid("/service/core/media/4711") shouldBe true
  }

  it should "resolve IDs through an application policy without allowing external rewrites" in {
    val restricted = new MediaUrlPolicy {
      def resolve(src: String): Option[MediaReference] =
        Option.when(src.startsWith("/media/"))(
          MediaReference(src, Some(src.stripPrefix("/media/")))
        )
    }
    MarkdownImages.parse("![Cat](/media/4711)", restricted).get.mediaId shouldBe Some("4711")
    MarkdownImages.parse("![Cat](/assets/cat.png)", restricted) shouldBe None
    val bad = new MediaUrlPolicy {
      def resolve(src: String) = Some(MediaReference("https://host/cat.png"))
    }
    MediaUrlPolicy.checked(bad, "/media/cat.png") shouldBe None
  }

  "Legacy cleanup" should "remove image data and preserve surrounding text and code" in {
    val image  = "![old](data:image/png;base64,YQ==)"
    val source = s"Before $image after\n\n`$image`\n\n```md\n$image\n```\n\n    $image"
    MarkdownImages.discardEmbedded(source) shouldBe
      s"Before  after\n\n`$image`\n\n```md\n$image\n```\n\n    $image"
  }

  it should "leave escaped image examples alone" in {
    val source = "\\![example](data:image/png;base64,YQ==)"
    MarkdownImages.discardEmbedded(source) shouldBe source
  }

  it should "remove referenced embedded images and their definitions" in {
    val source = "Before ![old][picture] after\n\n[picture]: data:image/png;base64,YQ=="
    MarkdownImages.discardEmbedded(source) shouldBe "Before  after\n\n"
    MarkdownImages.discardEmbedded("![old](<data:image/png;base64,YQ==>)") shouldBe ""
  }

  it should "resolve image references without breaking links that share the definition" in {
    val source =
      "![Cat][picture]{width=320} [Download][picture]\n\n[picture]: /media/cat.png \"Garden\""
    MarkdownImages.expandReferences(source) shouldBe
      "![Cat](/media/cat.png \"Garden\"){width=320} [Download](/media/cat.png \"Garden\")\n\n"
    MarkdownImages
      .validationError("![Cat][picture]\n\n[picture]: https://host/cat.png", policy)
      .isDefined shouldBe true
  }
}
