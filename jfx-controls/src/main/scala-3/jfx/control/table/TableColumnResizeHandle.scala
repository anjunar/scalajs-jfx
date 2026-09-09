package jfx.control.table

import jfx.core.component.AbstractComponent
import jfx.core.dsl.DslLayer
import jfx.core.dsl.ClassDsl.addClass
import jfx.core.dsl.EventDsl.on
import jfx.core.render.{Cursor, DomHostElement}
import jfx.core.state.Disposable
import org.scalajs.dom
import scala.scalajs.js
import scala.util.control.NonFatal

/** A separate hit target so resizing never bubbles into the header's sort action. */
private[table] final class TableColumnResizeHandle[S](
    table: TableView[S],
    column: TableColumn[S, ?]
) extends AbstractComponent {
  override val tagName               = "div"
  private var finishDrag: () => Unit = () => ()

  override def compose(cursor: Cursor): Unit = DslLayer.render(this, cursor) {
    addClass("jfx-table-column-resize-handle")
    setStyle("position", "absolute")
    setStyle("right", "0")
    setStyle("top", "0")
    setStyle("bottom", "0")
    setStyle("width", "8px")
    setStyle("cursor", "col-resize")
    setStyle("touch-action", "none")
    setStyle("user-select", "none")
    setAttribute("role", "separator")
    setAttribute("aria-orientation", "vertical")
    setAttribute("tabindex", "0")
    addDisposable(column.textProperty.observe(text => setAttribute("aria-label", s"Resize $text")))
    addDisposable(
      column.widthProperty.observe(width => setAttribute("aria-valuenow", width.toString))
    )
    def updateBounds(): Unit = {
      val spec = column.widthSpec(column.prefWidth)
      setAttribute("aria-valuemin", spec.minimum.toString)
      setAttribute("aria-valuemax", spec.maximum.toString)
    }
    addDisposable(column.minWidthProperty.observe(_ => updateBounds()))
    addDisposable(column.maxWidthProperty.observe(_ => updateBounds()))
    addDisposable(column.resizableProperty.observe { enabled =>
      setStyle("display", if (enabled) "block" else "none")
      if (!enabled) finishDrag()
    })
    addDisposable(table.columnResizePolicyProperty.observeWithoutInitial(_ => finishDrag()))
    addDisposable(Disposable(finishDrag()))
    on("click") { event => event.preventDefault(); event.stopPropagation() }
    on("dblclick") { event => event.preventDefault(); event.stopPropagation() }
    on("keydown") { event =>
      event.raw match {
        case key: dom.KeyboardEvent if key.key == "ArrowLeft" || key.key == "ArrowRight" =>
          event.preventDefault()
          event.stopPropagation()
          val step = if (key.shiftKey) 1.0 else 10.0
          table.resizeColumn(column, if (key.key == "ArrowLeft") -step else step)
        case _ => ()
      }
    }
    if (cursor.isBrowser) {
      on("lostpointercapture")(_ => finishDrag())
      on("pointerdown") { event =>
        event.raw match {
          case pointer: dom.PointerEvent if pointer.button == 0 && column.resizable =>
            event.preventDefault()
            event.stopPropagation()
            finishDrag()
            val pointerId                                  = pointer.pointerId
            val startX                                     = pointer.clientX
            val startWidth                                 = column.width
            val move: js.Function1[dom.PointerEvent, Unit] = next =>
              if (next.pointerId == pointerId) {
                next.preventDefault()
                table.resizeColumn(column, startWidth + next.clientX - startX - column.width)
              }
            val up: js.Function1[dom.PointerEvent, Unit] =
              next => if (next.pointerId == pointerId) finishDrag()
            val blur: js.Function1[dom.Event, Unit] = _ => finishDrag()
            dom.window.addEventListener("pointermove", move)
            dom.window.addEventListener("pointerup", up)
            dom.window.addEventListener("pointercancel", up)
            dom.window.addEventListener("blur", blur)
            val element = Option(host).collect { case host: DomHostElement =>
              host.node.asInstanceOf[dom.Element]
            }
            finishDrag = () => {
              finishDrag = () => ()
              dom.window.removeEventListener("pointermove", move)
              dom.window.removeEventListener("pointerup", up)
              dom.window.removeEventListener("pointercancel", up)
              dom.window.removeEventListener("blur", blur)
              element.foreach { node =>
                try { if (node.hasPointerCapture(pointerId)) node.releasePointerCapture(pointerId) }
                catch { case NonFatal(_) => () }
              }
            }
            element.foreach { node =>
              try node.setPointerCapture(pointerId)
              catch { case NonFatal(_) => () }
            }
          case _ => ()
        }
      }
    }
  }
}
