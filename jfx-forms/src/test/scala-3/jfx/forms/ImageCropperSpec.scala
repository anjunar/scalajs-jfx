package jfx.forms

import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.DslLayer.render
import jfx.core.render.{Cursor, SsrCursor}
import jfx.core.state.Property
import jfx.forms.ImageCropper.*
import jfx.layout.Viewport
import jfx.layout.Viewport.viewport
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ImageCropperSpec extends AnyFlatSpec with Matchers {

  "ImageCropper SSR" should "render a stable closed control with its placeholder" in {
    val html = Runtime.renderToString { cursor =>
      Runtime.mount(
        new CropperRoot {
          override protected def content(using AbstractComponent, Cursor): Unit =
            imageCropper("avatar", standalone = true) {
              placeholder = "Choose an avatar"
              aspectRatio = Some(1.0)
            }
        },
        cursor
      )
    }

    html should include("class=\"image-cropper-field image-cropper\"")
    html should include("name=\"avatar\"")
    html should include("accept=\"image/*\"")
    html should include("Choose an avatar")
    html should not include "image-cropper-dialog"
  }

  it should "prefer a thumbnail and react when its data changes" in {
    val thumbnail = new Thumbnail(
      contentType = Property("image/webp"),
      data = Property("first")
    )
    val media = new Media(
      contentType = Property("image/png"),
      data = Property("original"),
      thumbnail = Property(thumbnail)
    )
    val cursor                = new SsrCursor()
    var cropper: ImageCropper = null
    val root                  = Runtime.mount(
      new CropperRoot {
        override protected def content(using AbstractComponent, Cursor): Unit =
          cropper = imageCropper("avatar", standalone = true) {
            ImageCropper.value = media
          }
      },
      cursor
    )

    cursor.collectHtml() should include("data:image/webp;base64,first")
    thumbnail.data.set("second")
    cursor.collectHtml() should include("data:image/webp;base64,second")

    Runtime.unmount(root)
    thumbnail.data.set("after-unmount")
    cropper.valueProperty.get should be theSameInstanceAs media
  }

  it should "render readonly state without interactive toolbar actions" in {
    val html = Runtime.renderToString { cursor =>
      Runtime.mount(
        new CropperRoot {
          override protected def content(using AbstractComponent, Cursor): Unit =
            imageCropper("locked", standalone = true) {
              editable = false
            }
        },
        cursor
      )
    }

    html should include("aria-disabled=\"true\"")
    html should include("disabled=\"true\"")
    html should include("display: none")
  }

  "ImageCropper validation" should "combine control and image-data validators" in {
    var cropper: ImageCropper = null
    val cursor                = new SsrCursor()
    val root                  = Runtime.mount(
      new CropperRoot {
        override protected def content(using AbstractComponent, Cursor): Unit =
          cropper = imageCropper("avatar", standalone = true) {
            addValidator(ImageValidator("Image data is too short")(_.length >= 4))
            ImageCropper.value = new Media(data = Property("abc"))
          }
      },
      cursor
    )

    cropper.validate(forceVisible = true) shouldBe Seq("Image data is too short")
    cropper.errors.toSeq shouldBe Seq("Image data is too short")
    cropper.editableProperty.set(false)
    cropper.validate(forceVisible = true) shouldBe empty
    cropper.errors shouldBe empty

    Runtime.unmount(root)
  }

  "ImageCropper window integration" should "mount the crop dialog through Viewport" in {
    Viewport.windows.clear()
    val media = new Media(
      name = Property("avatar.png"),
      contentType = Property("image/png"),
      data = Property("encoded")
    )
    val cursor                = new SsrCursor()
    var cropper: ImageCropper = null
    val root                  = Runtime.mount(
      new CropperRoot {
        override protected def content(using AbstractComponent, Cursor): Unit =
          viewport {
            cropper = imageCropper("avatar", standalone = true) {
              ImageCropper.value = media
            }
          }
      },
      cursor
    )

    try {
      cropper.openEditor() should not be empty
      val html = cursor.collectHtml()
      html should include("class=\"image-cropper image-cropper-dialog\"")
      html should include("<canvas")
      Viewport.windows.length shouldBe 1
    } finally {
      Runtime.unmount(root)
      Viewport.windows.clear()
    }
  }

  "ImageCropper image helpers" should "preserve aspect ratio under output bounds" in {
    ImageCropper.scaledSize(1600, 900, Some(800), Some(800)) shouldBe (800 -> 450)
    ImageCropper.scaledSize(900, 1600, Some(800), Some(400)) shouldBe (225 -> 400)
    ImageCropper.scaledSize(100, 50, Some(800), Some(800)) shouldBe (100   -> 50)
  }

  it should "parse and preserve supported image URLs" in {
    ImageCropper.mimeTypeFromDataUrl("data:image/png;base64,abc") shouldBe Some("image/png")
    ImageCropper.base64FromDataUrl("data:image/png;base64,abc") shouldBe Some("abc")
    ImageCropper.toDataUrl("image/png", "abc") shouldBe Some("data:image/png;base64,abc")
    ImageCropper.toDataUrl("image/png", "https://example.test/image.png") shouldBe
      Some("https://example.test/image.png")
  }
}

private abstract class CropperRoot extends AbstractComponent {
  override val tagName: String = "main"
  protected def content(using AbstractComponent, Cursor): Unit

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      content
    }
}
