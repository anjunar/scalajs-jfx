package jfx.control.table

import jfx.core.layout.Div
import jfx.core.render.DomHostElement
import jfx.core.state.{CompositeDisposable, Disposable}
import org.scalajs.dom
import scala.scalajs.js
import scala.util.control.NonFatal

/** Pointer capture owns only the gesture; Runtime owns the eventual component move. */
private[table] final class TableColumnReorderGesture[S](
    table: TableView[S],
    column: TableColumn[S, ?],
    header: Div,
    sort: Boolean => Boolean,
    browser: Boolean
) extends Disposable {
  private val subscriptions      = new CompositeDisposable()
  private var finish: () => Unit = () => ()
  private var suppressClick      = false
  header.setAttribute("tabindex", "0")
  header.setStyle("user-select", "none")
  header.setStyle("touch-action", "pan-y")
  header.classCondition(
    "jfx-table-column-drop-before",
    table.columnDropMarker.map(_.contains(column -> true))
  )
  header.classCondition(
    "jfx-table-column-drop-after",
    table.columnDropMarker.map(_.contains(column -> false))
  )
  subscriptions.add(header.onDisposable("click") { event =>
    if (suppressClick) { event.preventDefault(); event.stopPropagation(); suppressClick = false }
    else
      event.raw match {
        case click: dom.MouseEvent if !click.defaultPrevented && click.button == 0 =>
          sort(click.shiftKey)
        case _ => ()
      }
  })
  subscriptions.add(column.reorderableProperty.observeWithoutInitial(_ => finish()))
  subscriptions.add(table.visibleLeafColumns.observeWithoutInitial(_ => finish()))
  if (browser) {
    subscriptions.add(header.onDisposable("keydown") { event =>
      event.raw match {
        case key: dom.KeyboardEvent
            if !key.defaultPrevented && !key.isComposing && !key.repeat &&
              !key.altKey && !key.ctrlKey && !key.metaKey &&
              (key.key == "Enter" || key.key == " ") &&
              key.target == header.host.asInstanceOf[DomHostElement].node =>
          if (sort(key.shiftKey)) { event.preventDefault(); event.stopPropagation() }
        case key: dom.KeyboardEvent
            if key.altKey && key.shiftKey &&
              (key.key == "ArrowLeft" || key.key == "ArrowRight") =>
          event.preventDefault()
          event.stopPropagation()
          if (column.reorderable) {
            finish()
            table.moveColumn(
              column,
              table.getVisibleLeafIndex(column) +
                (if (key.key == "ArrowLeft") -1 else 1)
            )
          }
        case _ => ()
      }
    })
    subscriptions.add(header.onDisposable("lostpointercapture")(_ => finish()))
    subscriptions.add(header.onDisposable("pointerdown") { event =>
      event.raw match {
        case pointer: dom.PointerEvent if pointer.button == 0 =>
          suppressClick = false
          if (column.reorderable && table.canMoveColumns) {
            event.preventDefault() // Retain focus and selection in an active cell editor.
            table.cancelColumnDrag()
            val pointerId                            = pointer.pointerId
            val startX                               = pointer.clientX
            val startY                               = pointer.clientY
            var dragging                             = false
            var boundary: Option[Int]                = None
            def update(next: dom.PointerEvent): Unit = {
              if (!table.canMoveColumns) finish()
              else {
                if (!dragging && math.hypot(next.clientX - startX, next.clientY - startY) >= 5) {
                  dragging = true
                  suppressClick = true
                  header.addClass("jfx-table-column-dragging")
                }
                if (dragging) {
                  boundary = table.columnDropAt(next.clientX, next.clientY)
                  table.markColumnDrop(boundary)
                }
              }
            }
            val move: js.Function1[dom.PointerEvent, Unit] =
              next => if (next.pointerId == pointerId) { next.preventDefault(); update(next) }
            val up: js.Function1[dom.PointerEvent, Unit] = next =>
              if (next.pointerId == pointerId) {
                update(next)
                val target = if (dragging) boundary else None
                finish()
                target.foreach { index =>
                  val from = table.getVisibleLeafIndex(column)
                  if (column.reorderable)
                    table.moveColumn(column, if (index > from) index - 1 else index)
                }
              }
            val cancel: js.Function1[dom.PointerEvent, Unit] =
              next => if (next.pointerId == pointerId) finish()
            val blur: js.Function1[dom.Event, Unit]           = _ => finish()
            val escape: js.Function1[dom.KeyboardEvent, Unit] =
              key => if (key.key == "Escape") { key.preventDefault(); finish() }
            val element = header.host.asInstanceOf[DomHostElement].node.asInstanceOf[dom.Element]
            dom.window.addEventListener("pointermove", move)
            dom.window.addEventListener("pointerup", up)
            dom.window.addEventListener("pointercancel", cancel)
            dom.window.addEventListener("blur", blur)
            dom.window.addEventListener("keydown", escape)
            finish = () => {
              finish = () => ()
              table.cancelColumnDrag = () => ()
              dom.window.removeEventListener("pointermove", move)
              dom.window.removeEventListener("pointerup", up)
              dom.window.removeEventListener("pointercancel", cancel)
              dom.window.removeEventListener("blur", blur)
              dom.window.removeEventListener("keydown", escape)
              header.removeClass("jfx-table-column-dragging")
              table.markColumnDrop(None)
              try {
                if (element.hasPointerCapture(pointerId)) element.releasePointerCapture(pointerId)
              } catch { case NonFatal(_) => () }
            }
            table.cancelColumnDrag = () => finish()
            try element.setPointerCapture(pointerId)
            catch { case NonFatal(_) => () }
          }
        case _ => ()
      }
    })
  }
  override def dispose(): Unit = { finish(); subscriptions.dispose() }
}
