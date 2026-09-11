package ui.viewport

import ui.core.component.AbstractComponent
import ui.core.dsl.ClassDsl.{classIf, classes}
import ui.core.dsl.DslLayer.{child, render, renderInto}
import ui.core.dsl.EventDsl
import ui.core.dsl.EventDsl.*
import ui.core.dsl.StyleDsl.*
import ui.core.layout.Button.{button, buttonType}
import ui.core.layout.Div
import ui.core.layout.Div.div
import ui.core.layout.TextComponent.text
import ui.core.render.Cursor
import ui.core.render.UiEvent
import org.scalajs.dom

import scala.scalajs.js

final class Window(conf: Viewport.WindowConf) extends AbstractComponent {
  val tagName = "div"

  private var containerHost: Div = _

  override def compose(cursor: Cursor): Unit = {
    given AbstractComponent = this

    render(this, cursor) {
      addClass("ui-window")
      classIf("is-hidden", conf.visible.map(!_))

      style {
        position = "absolute"
        left = conf.leftPx.map(px => s"${px.round}px")
        top = conf.topPx.map(px => s"${px.round}px")
        width = s"${conf.widthPx}px"
        height = s"${conf.heightPx}px"
        zIndex = conf.zIndex.map(_.toString)
      }

      onClick { _ =>
        Viewport.touchWindow(conf)
        conf.onClick.foreach(_(this))
      }

      div {
        classes = Seq("ui-window__surface")

        div {
          classes = Seq("ui-window__header")

          on("mousedown") { event =>
            startDrag(event)
          }

          div {
            classes = Seq("ui-window__title")
            text(conf.title) {}
          }

          div {
            classes = Seq("ui-window__actions")

            button("close") {
              classes = Seq("material-icons", "ui-window__chrome-button")
              buttonType("button")

              onClick { event =>
                event.stopPropagation()
                conf.onClose.foreach(_(this))
                Viewport.closeWindow(conf)
              }
            }
          }
        }

        containerHost = div {
          classes = Seq("ui-window__container")
        }
      }
    }

    renderInto(containerHost) {
      conf.body
    }
  }

  private def startDrag(event: UiEvent): Unit =
    event.raw match {
      case mouse: dom.MouseEvent =>
        event.preventDefault()
        Viewport.touchWindow(conf)

        val startX      = mouse.clientX.toDouble
        val startY      = mouse.clientY.toDouble
        val initialLeft = conf.leftPx.get
        val initialTop  = conf.topPx.get

        val moveListener: js.Function1[dom.MouseEvent, Any] = next =>
          conf.leftPx.set(initialLeft + next.clientX.toDouble - startX)
          conf.topPx.set(initialTop + next.clientY.toDouble - startY)

        var upListener: js.Function1[dom.MouseEvent, Any] = null
        upListener = _ =>
          dom.window.removeEventListener("mousemove", moveListener)
          dom.window.removeEventListener("mouseup", upListener)

        dom.window.addEventListener("mousemove", moveListener)
        dom.window.addEventListener("mouseup", upListener)
      case _ =>
        ()
    }
}

object Window {
  def window(conf: Viewport.WindowConf)(using AbstractComponent, Cursor): Window =
    child(new Window(conf)) {}
}
