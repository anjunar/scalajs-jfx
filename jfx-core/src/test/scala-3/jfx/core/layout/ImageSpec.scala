package jfx.core.layout

import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.DslLayer
import jfx.core.layout.Image.*
import jfx.core.render.{Cursor, SsrCursor}
import jfx.core.state.Property
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ImageSpec extends AnyFlatSpec with Matchers {

  "Image" should "render its native image attributes during SSR" in {
    val html = Runtime.renderToString { cursor =>
      Runtime.mount(
        new ImageRoot {
          override protected def content(using AbstractComponent, Cursor): Unit =
            image {
              src = "/assets/profile.png"
              alt = "Profile & portrait"
              loading = "lazy"
              srcset = "/assets/profile.png 1x, /assets/profile@2x.png 2x"
              intrinsicWidth = 640
              intrinsicHeight = 480
            }
        },
        cursor
      )
    }

    html shouldBe
      "<main><img src=\"/assets/profile.png\" alt=\"Profile &amp; portrait\" loading=\"lazy\" srcset=\"/assets/profile.png 1x, /assets/profile@2x.png 2x\" width=\"640\" height=\"480\"></main>"
  }

  it should "update reactive source and alternative text without changing its structure" in {
    val source          = Property("/assets/first.png")
    val alternative     = Property("First image")
    val cursor          = new SsrCursor()
    var rendered: Image = null

    val root = Runtime.mount(
      new ImageRoot {
        override protected def content(using AbstractComponent, Cursor): Unit =
          rendered = image {
            src = source
            alt = alternative
          }
      },
      cursor
    )

    cursor.collectHtml() shouldBe
      "<main><img src=\"/assets/first.png\" alt=\"First image\"></main>"

    source.set("   ")
    alternative.set("Second image")

    cursor.collectHtml() shouldBe
      "<main><img alt=\"Second image\"></main>"

    val imageHost = rendered.host
    Runtime.unmount(root)
    source.set("/assets/ignored.png")
    alternative.set("Ignored")

    imageHost.attribute("src") shouldBe None
    imageHost.attribute("alt") shouldBe Some("Second image")
  }
}

private abstract class ImageRoot extends AbstractComponent {
  override val tagName: String = "main"

  protected def content(using AbstractComponent, Cursor): Unit

  override def compose(cursor: Cursor): Unit =
    DslLayer.render(this, cursor) {
      content
    }
}
