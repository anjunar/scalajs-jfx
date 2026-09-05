import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.EventDsl.onClick
import jfx.core.dsl.DslLayer.render
import jfx.core.layout.Button.button
import jfx.core.layout.TextComponent.text
import jfx.core.layout.VBox.vbox
import jfx.core.render.{Cursor, DomCursor}
import jfx.core.state.Property
import org.scalajs.dom

final class Counter extends AbstractComponent {
  val tagName = "main"

  override def compose(cursor: Cursor): Unit = {
    render(this, cursor) {
      val count = Property(0)

      vbox {
        text(count.map(n => s"Count: $n")) {}
        button("Increment") {
          onClick(_ => count.set(count.get + 1))
        }
      }
    }
  }
}

object Main {
  def main(args: Array[String]): Unit =
    Runtime.mount(new Counter, DomCursor.root(dom.document.getElementById("root")))
}

