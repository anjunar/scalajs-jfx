package app.components

import app.components.Dsl.addClass
import jfx.core.component.AbstractComponent
import jfx.core.layout.Div.div
import jfx.core.render.Cursor

object Layouts {
  def vbox(body: AbstractComponent ?=> Cursor ?=> Unit)(using AbstractComponent, Cursor): Unit =
    div {
      addClass("vbox")
      body
    }

  def hbox(body: AbstractComponent ?=> Cursor ?=> Unit)(using AbstractComponent, Cursor): Unit =
    div {
      addClass("hbox")
      body
    }
}
