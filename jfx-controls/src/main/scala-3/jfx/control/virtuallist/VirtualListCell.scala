package jfx.control.virtuallist

import jfx.control.virtuallist.VirtualListView.{Renderer, VisibleSlot}
import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.addClass
import jfx.core.dsl.DslLayer
import jfx.core.dsl.StyleDsl.*
import jfx.core.render.{Cursor, DomHostElement}
import jfx.core.state.Disposable
import org.scalajs.dom

private final class VirtualListCell[T](
    slot: VisibleSlot[T],
    renderer: Renderer[T],
    onMeasured: (Int, Double) => Unit
) extends AbstractComponent {
  override val tagName: String = "div"

  override def compose(cursor: Cursor): Unit =
    DslLayer.render(this, cursor) {
      addClass("jfx-virtual-list-cell")
      if (slot.item.isEmpty) addClass("jfx-virtual-list-cell-loading")
      host.setAttribute("data-item-index", slot.index.toString)
      style {
        position = "absolute"
        left = "0"
        top = s"${slot.top}px"
        width = "100%"
        minHeight = s"${slot.height}px"
        boxSizing = "border-box"
      }

      renderer(slot.item.orNull, slot.index)(using this)(using cursor)
    }

  override def afterCompose(cursor: Cursor): Unit =
    if (cursor.isBrowser) {
      domElement.foreach { element =>
        var active = true
        val measure = () => {
          if (active) {
            val height = element.offsetHeight.toDouble
            if (height > 0) onMeasured(slot.index, height)
          }
        }

        val frame = dom.window.requestAnimationFrame(_ => measure())
        val observer = new dom.ResizeObserver((_, _) => measure())
        observer.observe(element)
        addDisposable(Disposable {
          active = false
          dom.window.cancelAnimationFrame(frame)
          observer.disconnect()
        })
      }
    }

  private def domElement: Option[dom.html.Element] =
    host match {
      case domHost: DomHostElement =>
        domHost.node match {
          case element: dom.html.Element => Some(element)
          case _                         => None
        }
      case _ => None
    }
}
