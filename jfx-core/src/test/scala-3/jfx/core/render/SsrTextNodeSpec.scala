package jfx.core.render

import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.DslLayer.render
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.state.Property
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** An empty text node serializes to nothing, so a browser parses no node where the client tree has
  * one and hydration fails with "There is no further DOM node". It cost the demo's forms route its
  * hydration: `InputContainer` renders a bound text for the error message, which is empty until a
  * validator fires.
  */
class SsrTextNodeSpec extends AnyFlatSpec with Matchers {

  "An empty text node" should "leave an anchor in the server-rendered HTML" in {
    val html = Runtime.renderToString { cursor =>
      Runtime.mount(new TextRoot(Property("")), cursor)
    }

    html shouldBe "<main><div><!--jfx:text--></div></main>"
  }

  it should "leave the anchor when a bound text becomes empty again" in {
    val message = Property("Required")
    val cursor  = new SsrCursor()

    Runtime.mount(new TextRoot(message), cursor)
    cursor.collectHtml() shouldBe "<main><div>Required</div></main>"

    message.set("")
    cursor.collectHtml() shouldBe "<main><div><!--jfx:text--></div></main>"
  }

  it should "not anchor a text that carries content" in {
    val html = Runtime.renderToString { cursor =>
      Runtime.mount(new TextRoot(Property("Ada & Grace")), cursor)
    }

    html shouldBe "<main><div>Ada &amp; Grace</div></main>"
  }
}

private final class TextRoot(message: Property[String]) extends AbstractComponent {
  val tagName = "main"

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      div {
        text(message) {}
      }
    }
}
