package jfx.core.dsl

import jfx.core.render.UiEvent
import jfx.core.state.Disposable
import org.scalajs.dom

trait EventDsl {

  def onHandler(eventName: String)(handler: UiEvent => Unit): Unit

  def onClickHandler(handler: UiEvent => Unit): Unit

  def onDoubleClickHandler(handler: UiEvent => Unit): Unit

  def onWindowKeyDownHandler(handler: dom.KeyboardEvent => Unit): Unit

  /** Like [[onHandler]], but hands the [[Disposable]] back to the caller instead of tying its
    * lifecycle to this component. Use this when a listener on this component's host must be
    * disposed together with a *different* component (e.g. the component that created it).
    */
  def onDisposable(eventName: String)(handler: UiEvent => Unit): Disposable

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
