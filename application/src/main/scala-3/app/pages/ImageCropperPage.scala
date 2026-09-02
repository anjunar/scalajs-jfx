package app.pages

import app.components.Showcase
import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.classes
import jfx.core.dsl.StyleDsl.*
import jfx.core.layout.Div.div
import jfx.core.layout.HBox.hbox
import jfx.core.layout.Image.{alt, image, src}
import jfx.core.layout.TextComponent.text
import jfx.core.layout.VBox.vbox
import jfx.core.render.Cursor
import jfx.core.state.Property
import jfx.domain.Media
import jfx.forms.ImageCropper.*
import jfx.i18n.i18n

object ImageCropperPage {
  def render()(using AbstractComponent, Cursor): Unit = {
    val croppedMedia = Property[Media](null)

    Showcase.showcasePage(
      i18n"Image cropper",
      i18n"Upload, crop and bind images through the regular forms contract."
    ) {
      Showcase.sectionIntro(
        i18n"Upload and crop",
        i18n"One control, two image sizes",
        i18n"The cropper stores a bounded main image and generates a separate thumbnail while keeping file readers, canvas interaction and its viewport window lifecycle-bound."
      )

      Showcase.componentShowcase(
        i18n"Crop a profile image",
        i18n"Choose an image, adjust the square selection and apply it in the viewport window."
      ) {
        div {
          classes = Seq("image-cropper-page__layout")

          imageCropper("profile-image", standalone = true) {
            classes = Seq("image-cropper-page__control")
            placeholder = i18n"Choose a profile image"
            aspectRatio = Some(1.0)
            outputMaxWidth = Some(800)
            outputMaxHeight = Some(800)
            thumbnailMaxWidth = 160
            thumbnailMaxHeight = 160
            windowTitle = i18n"Crop profile image"
            summon[jfx.forms.ImageCropper].addDisposable(
              valueProperty.observe(croppedMedia.set)
            )
          }

          vbox {
            classes = Seq("image-cropper-page__result")

            div {
              classes = Seq("image-cropper-page__result-label")
              text(i18n"Generated thumbnail") {}
            }

            hbox {
              classes = Seq("image-cropper-page__result-row")
              image {
                classes = Seq("image-cropper-page__thumbnail")
                src = croppedMedia.map(thumbnailSource)
                alt = i18n"Cropped profile image"
              }
              div {
                classes = Seq("image-cropper-page__result-copy")
                text(croppedMedia.map(resultDescription)) {}
              }
            }
          }
        }
      }

      Showcase.componentShowcase(
        i18n"Readonly state",
        i18n"Editability comes from the same control contract used by Input and ComboBox."
      ) {
        imageCropper("locked-image", standalone = true) {
          classes = Seq("image-cropper-page__readonly")
          placeholder = i18n"Image selection is disabled"
          editable = false
        }
      }

      Showcase.apiSection(
        i18n"Contextual DSL",
        i18n"ImageCropper remains a normal typed control and can participate in model binding."
      ) {
        Showcase.codeBlock(
          "scala",
          """imageCropper("profileImage") {
            |  placeholder = "Choose a profile image"
            |  aspectRatio = Some(1.0)
            |  outputMaxWidth = Some(800)
            |  outputMaxHeight = Some(800)
            |  thumbnailMaxWidth = 160
            |  thumbnailMaxHeight = 160
            |  windowTitle = "Crop profile image"
            |}""".stripMargin
        )
      }
    }
  }

  private def thumbnailSource(media: Media): String =
    Option(media)
      .flatMap(value => Option(value.thumbnail.get))
      .flatMap { thumbnail =>
        toDataUrl(thumbnail.contentType.get, thumbnail.data.get)
      }
      .getOrElse("")

  private def toDataUrl(contentType: String, dataOrUrl: String): Option[String] = {
    val data = Option(dataOrUrl).map(_.trim).getOrElse("")
    if (data.isEmpty) None
    else if (
      data.startsWith("data:") || data.startsWith("http://") ||
      data.startsWith("https://") || data.startsWith("blob:")
    ) Some(data)
    else
      Option(contentType).map(_.trim).filter(_.nonEmpty).map { normalizedType =>
        s"data:$normalizedType;base64,$data"
      }
  }

  private def resultDescription(media: Media): String =
    Option(media) match {
      case None        => "No cropped image yet."
      case Some(value) =>
        val mainSize      = Option(value.data.get).fold(0)(_.length)
        val thumbnailSize = Option(value.thumbnail.get)
          .flatMap(thumbnail => Option(thumbnail.data.get))
          .fold(0)(_.length)
        s"${value.contentType.get}, main data: $mainSize characters, thumbnail data: $thumbnailSize characters"
    }
}
