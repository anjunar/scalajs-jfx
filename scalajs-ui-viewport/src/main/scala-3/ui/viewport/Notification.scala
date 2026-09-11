package ui.viewport

import ui.core.component.AbstractComponent
import ui.core.dsl.ClassDsl.classIf
import ui.core.dsl.DslLayer.{child, render}
import ui.core.dsl.EventDsl.onClick
import ui.core.layout.TextComponent.text
import ui.core.render.Cursor

final class Notification(conf: Viewport.NotificationConf) extends AbstractComponent {
  val tagName = "div"

  override def compose(cursor: Cursor): Unit = {

    render(this, cursor) {
      addClass("scalajs-ui-viewport-notification")
      addClass(conf.kind.cssClass)
      classIf("is-hidden", conf.visible.map(!_))

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
