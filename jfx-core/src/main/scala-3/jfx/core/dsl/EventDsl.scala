package jfx.core.dsl

import jfx.core.render.UiEvent
import org.scalajs.dom

trait EventDsl {

  def onHandler(eventName: String)(handler: UiEvent => Unit): Unit

  def onClickHandler(handler: UiEvent => Unit): Unit

  def onDoubleClickHandler(handler: UiEvent => Unit): Unit

  def onWindowKeyDownHandler(handler: dom.KeyboardEvent => Unit): Unit

}

object EventDsl {

  def on(eventName: String)(handler: UiEvent => Unit)(using componentDsl: EventDsl): Unit =
    componentDsl.onHandler(eventName)(handler)

  def onClick(handler: UiEvent => Unit)(using componentDsl: EventDsl): Unit =
    componentDsl.onClickHandler(handler)

  def onDoubleClick(handler: UiEvent => Unit)(using componentDsl: EventDsl): Unit =
    componentDsl.onDoubleClickHandler(handler)

  def onWindowKeyDown(handler: dom.KeyboardEvent => Unit)(using componentDsl: EventDsl): Unit =
    componentDsl.onWindowKeyDownHandler(handler)

}
