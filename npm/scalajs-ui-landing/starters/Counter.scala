import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.EventDsl.onClick
import ui.core.dsl.DslLayer.render
import ui.core.layout.Button.button
import ui.core.layout.TextComponent.text
import ui.core.layout.VBox.vbox
import ui.core.render.{Cursor, DomCursor}
import ui.core.state.Property
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
