package jfx.control.table

import jfx.core.component.AbstractComponent
import jfx.core.dsl.DslLayer
import jfx.core.layout.Button
import jfx.core.layout.Button.button
import jfx.core.render.{Cursor, DomHostElement}
import jfx.core.state.{Disposable, Property}
import jfx.viewport.{Overlay, Viewport}
import org.scalajs.dom
import scala.scalajs.js

/** The table owns the menu lifetime; Viewport owns its overlay, anchoring and positioning. */
private[table] final class TableColumnMenu[S](table: TableView[S]) extends AbstractComponent {
  override val tagName                                               = "div"
  private var trigger: Button                                        = null
  private var registration: Option[Viewport.OverlayConf]             = None
  private var ready                                                  = false
  private var entries                                                = Vector.empty[Button]
  private def element(component: AbstractComponent): dom.HTMLElement =
    component.host.asInstanceOf[DomHostElement].node.asInstanceOf[dom.HTMLElement]

  private def focusEntry(index: Int): Unit = if (entries.nonEmpty) {
    val next = (index % entries.size + entries.size) % entries.size
    entries.zipWithIndex.foreach { (entry, i) =>
      entry.setAttribute("tabindex", if (i == next) "0" else "-1")
    }
    element(entries(next)).focus()
  }

  private def close(returnFocus: Boolean): Unit = {
    val previous = registration
    registration = None
    previous.foreach(Viewport.closeOverlay)
    if (!trigger.isDisposed) {
      trigger.setAttribute("aria-expanded", "false")
      trigger.removeAttribute("aria-controls")
      if (returnFocus && ready) element(trigger).focus()
    }
  }

  private def open(last: Boolean = false): Unit = if (ready && table.columns.nonEmpty) {
    if (registration.isEmpty) {
      table.cancelColumnDrag()
      val width  = math.min(240.0, math.max(0.0, dom.window.innerWidth - 16.0))
      val menuId = TableColumnMenu.nextId()
      val conf   = new Viewport.OverlayConf(
        anchor = Some(element(trigger)),
        widthPx = Some(width),
        effectiveWidthProperty = Property(width),
        offsetXPx = element(trigger).getBoundingClientRect().width - width,
        body = (_: Overlay) ?=>
          (_: Cursor) ?=> {
            val popup = summon[Overlay]
            popup.addClass("jfx-table-column-menu-panel")
            popup.setAttribute("role", "menu")
            popup.setAttribute("id", menuId)
            popup.addDisposable(
              table.columnMenuTextProperty.observe(popup.setAttribute("aria-label", _))
            )
            entries = table.columns.toVector.zipWithIndex.map { (column, index) =>
              button(column.textProperty) {
                val item = summon[Button]
                item.addClass("jfx-table-column-menu-item")
                item.setAttribute("type", "button")
                item.setAttribute("role", "menuitemcheckbox")
                item.addDisposable(column.textProperty.observe(item.setAttribute("aria-label", _)))
                item.setAttribute("tabindex", "-1")
                item.addDisposable(
                  column.visibleProperty.observe(value =>
                    item.setAttribute("aria-checked", value.toString)
                  )
                )
                item.onHandler("click") { event =>
                  event.stopPropagation()
                  if (table.canMoveColumns) column.visible = !column.visible
                }
                item.onHandler("keydown") { event =>
                  event.raw match {
                    case key: dom.KeyboardEvent =>
                      key.key match {
                        case "ArrowDown" => event.preventDefault(); focusEntry(index + 1)
                        case "ArrowUp"   => event.preventDefault(); focusEntry(index - 1)
                        case "Home"      => event.preventDefault(); focusEntry(0)
                        case "End"       => event.preventDefault(); focusEntry(entries.size - 1)
                        case "Escape"    =>
                          event.preventDefault(); event.stopPropagation(); close(true)
                        case "Tab" =>
                          close(true) // Let the native Tab action continue from the trigger.
                        case _ => () // Buttons supply native Enter/Space activation.
                      }
                    case _ => ()
                  }
                }
              }
            }
            val outside: js.Function1[dom.Event, Unit] = event =>
              event.target match {
                case node: dom.Node
                    if !element(popup).contains(node) && !element(trigger).contains(node) =>
                  close(false)
                case _ => ()
              }
            val blur: js.Function1[dom.Event, Unit] = _ => close(false)
            dom.document.addEventListener("pointerdown", outside, true)
            dom.document.addEventListener("focusin", outside)
            dom.window.addEventListener("blur", blur)
            popup.addDisposable(Disposable {
              dom.document.removeEventListener("pointerdown", outside, true)
              dom.document.removeEventListener("focusin", outside)
              dom.window.removeEventListener("blur", blur)
              entries = Vector.empty
              registration = None
              if (!trigger.isDisposed) {
                trigger.setAttribute("aria-expanded", "false")
                trigger.removeAttribute("aria-controls")
              }
            })
          }
      )
      registration = Some(conf)
      try {
        Viewport.addOverlay(conf)(using this)
        trigger.setAttribute("aria-controls", menuId)
        trigger.setAttribute("aria-expanded", "true")
      } catch {
        case error: Throwable => close(false); throw error
      }
    }
    focusEntry(if (last) entries.size - 1 else 0)
  }

  override def compose(cursor: Cursor): Unit = DslLayer.render(this, cursor) {
    Viewport.requireCurrent(using this) // Fail at configuration, not at the first click.
    addClass("jfx-table-column-menu")
    trigger = button(table.columnMenuTextProperty) {
      val control = summon[Button]
      control.addClass("jfx-table-column-menu-button")
      control.setAttribute("type", "button")
      control.setAttribute("aria-haspopup", "menu")
      control.setAttribute("aria-expanded", "false")
      control.setAttribute("disabled", "")
      control.onHandler("click") { event =>
        event.stopPropagation()
        if (registration.nonEmpty) close(true) else open()
      }
      control.onHandler("keydown") { event =>
        event.raw match {
          case key: dom.KeyboardEvent if key.key == "ArrowDown" || key.key == "ArrowUp" =>
            event.preventDefault(); open(key.key == "ArrowUp")
          case key: dom.KeyboardEvent if key.key == "Escape" =>
            event.preventDefault(); close(true)
          case _ => ()
        }
      }
    }
    addDisposable(table.columns.observeWithoutInitial { columns =>
      close(ready && registration.nonEmpty)
      if (ready) trigger.disabled = columns.isEmpty
    })
    addDisposable(Disposable { ready = false; close(false) })
    if (cursor.isBrowser) cursor.afterHydration { () =>
      if (!isDisposed) { ready = true; trigger.disabled = table.columns.isEmpty }
    }
  }
}

private[table] object TableColumnMenu {
  private var sequence = 0L
  def nextId(): String = {
    sequence += 1
    val id = s"jfx-table-column-menu-$sequence"
    if (dom.document.getElementById(id) == null) id else nextId()
  }
}
