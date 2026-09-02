package jfx.core.dsl

import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.StyleDsl.*
import jfx.core.render.{Cursor, SsrCursor}
import jfx.core.state.Property
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class StyleDslSpec extends AnyFlatSpec with Matchers {

  "StyleDsl" should "set a property it does not name without a change to jfx-core" in {
    val html = Runtime.renderToString { cursor =>
      Runtime.mount(
        new StyleRoot {
          override protected def content(using AbstractComponent, Cursor): Unit =
            style {
              css("aspect-ratio", "16 / 9")
              css("scroll-margin-block", "2rem")
            }
        },
        cursor
      )
    }

    html shouldBe "<main style=\"aspect-ratio: 16 / 9; scroll-margin-block: 2rem\"></main>"
  }

  it should "bind an unnamed property to a reactive value" in {
    val ratio  = Property("16 / 9")
    val cursor = new SsrCursor()

    val root = Runtime.mount(
      new StyleRoot {
        override protected def content(using AbstractComponent, Cursor): Unit =
          style {
            css("aspect-ratio", ratio)
          }
      },
      cursor
    )

    cursor.collectHtml() shouldBe "<main style=\"aspect-ratio: 16 / 9\"></main>"

    ratio.set("4 / 3")
    cursor.collectHtml() shouldBe "<main style=\"aspect-ratio: 4 / 3\"></main>"

    Runtime.unmount(root)
    ratio.set("1 / 1")
  }

  it should "accept a reactive value for every named property, not only a chosen few" in {
    val paddingValue = Property("4px")
    val cursor       = new SsrCursor()

    Runtime.mount(
      new StyleRoot {
        override protected def content(using AbstractComponent, Cursor): Unit =
          style {
            padding = paddingValue
          }
      },
      cursor
    )

    cursor.collectHtml() shouldBe "<main style=\"padding: 4px\"></main>"

    paddingValue.set("8px")
    cursor.collectHtml() shouldBe "<main style=\"padding: 8px\"></main>"
  }

  it should "remove a property by its CSS name" in {
    val html = Runtime.renderToString { cursor =>
      Runtime.mount(
        new StyleRoot {
          override protected def content(using AbstractComponent, Cursor): Unit =
            style {
              css("aspect-ratio", "16 / 9")
              width = "10px"
              clearCss("aspect-ratio")
            }
        },
        cursor
      )
    }

    html shouldBe "<main style=\"width: 10px\"></main>"
  }

  it should "read back the inline value it set, through the name and through css" in {
    var namedRead   = "unset"
    var genericRead = "unset"
    var missingRead = "unset"

    Runtime.renderToString { cursor =>
      Runtime.mount(
        new StyleRoot {
          override protected def content(using AbstractComponent, Cursor): Unit =
            style {
              width = "240px"
              css("aspect-ratio", "16 / 9")

              namedRead = width
              genericRead = css("aspect-ratio")
              missingRead = height
            }
        },
        cursor
      )
    }

    namedRead shouldBe "240px"
    genericRead shouldBe "16 / 9"
    missingRead shouldBe ""
  }
}

private abstract class StyleRoot extends AbstractComponent {
  override val tagName: String = "main"

  protected def content(using AbstractComponent, Cursor): Unit

  override def compose(cursor: Cursor): Unit =
    DslLayer.render(this, cursor) {
      content
    }
}
