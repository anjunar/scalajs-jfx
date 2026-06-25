package jfx.layout

import jfx.core.component.AbstractComponent
import jfx.core.dsl.DslLayerTwo.{child, render}
import jfx.core.dsl.StyleDsl.*
import jfx.core.layout.TextComponent.text
import jfx.core.render.Cursor

final class Notification(conf: Viewport.NotificationConf) extends AbstractComponent {
  val tagName = "div"

  override def compose(cursor: Cursor): Unit = {

    render(this, cursor) {
      addClass("jfx-viewport-notification")
      addClass(conf.kind.cssClass)
      classIf("is-hidden", conf.visible.map(!_))

      style {
        top = s"${conf.topPx.round}px"
      }

      onClick { _ =>
        Viewport.closeNotification(conf)
      }

      text(conf.message) {}
    }
    
  }
}

object Notification {
  def notification(conf: Viewport.NotificationConf)(using AbstractComponent, Cursor): Notification =
    child(new Notification(conf)) {}
}
