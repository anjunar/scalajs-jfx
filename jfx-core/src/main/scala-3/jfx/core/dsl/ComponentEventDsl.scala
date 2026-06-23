package jfx.core.dsl

import jfx.core.component.AbstractComponent
import jfx.core.render.UiEvent
import jfx.core.state.Disposable
import org.scalajs.dom

import scala.scalajs.js

trait ComponentEventDsl {

  def on(eventName: String)(handler: UiEvent => Unit)(using component: AbstractComponent): Unit =
    component.addDisposable(component.host.on(eventName)(handler))

  def onClick(handler: UiEvent => Unit)(using component: AbstractComponent): Unit =
    on("click")(handler)

  def onDoubleClick(handler: UiEvent => Unit)(using component: AbstractComponent): Unit =
    on("dblclick")(handler)

  def onWindowKeyDown(handler: dom.KeyboardEvent => Unit)(using component: AbstractComponent): Unit =
    browserWindow.foreach { window =>
      val listener: js.Function1[dom.KeyboardEvent, Any] = event => handler(event)
      window.addEventListener("keydown", listener)
      component.addDisposable(Disposable(window.removeEventListener("keydown", listener)))
    }

  private def browserWindow: Option[dom.Window] =
    Option.when(js.typeOf(js.Dynamic.global.selectDynamic("window")) != "undefined")(dom.window)
}
