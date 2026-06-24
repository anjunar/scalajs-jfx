package jfx.core.dsl

import jfx.core.render.UiEvent
import org.scalajs.dom

trait EventDsl {

  def on(eventName: String)(handler: UiEvent => Unit): Unit

  def onClick(handler: UiEvent => Unit): Unit

  def onDoubleClick(handler: UiEvent => Unit): Unit

  def onWindowKeyDown(handler: dom.KeyboardEvent => Unit): Unit

}

object EventDsl {

  def on(eventName: String)(handler: UiEvent => Unit)(using componentDsl: EventDsl): Unit =
    componentDsl.on(eventName)(handler)

  def onClick(handler: UiEvent => Unit)(using componentDsl: EventDsl): Unit =
    componentDsl.onClick(handler)

  def onDoubleClick(handler: UiEvent => Unit)(using componentDsl: EventDsl): Unit =
    componentDsl.onDoubleClick(handler)

  def onWindowKeyDown(handler: dom.KeyboardEvent => Unit)(using componentDsl: EventDsl): Unit =
    componentDsl.onWindowKeyDown(handler)

}
