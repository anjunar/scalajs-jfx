package jfx.bridge

import jfx.core.component.{AbstractComponent}
import jfx.core.state.{Disposable => CoreDisposable}

import scala.scalajs.js

/** The JS projection of `AbstractComponent` plus its DSL traits. Mirrors `contract.ts`'s
  * `ComponentHandle`.
  */
final class ComponentHandleBridge(private[bridge] final val underlying: AbstractComponent)
    extends js.Object {

  def tagName: String = underlying.tagName

  def addClass(name: String): Unit = underlying.addClass(name)

  def removeClass(name: String): Unit = underlying.removeClass(name)

  def setClasses(names: js.Array[String]): Unit = underlying.setClasses(names.toSeq)

  def classIf(name: String, condition: JsReadOnlyProperty[Boolean]): Unit =
    underlying.classCondition(name, ReactiveBridge.wrap(condition))

  def setAttribute(name: String, value: String): Unit = underlying.setAttribute(name, value)

  def removeAttribute(name: String): Unit = underlying.removeAttribute(name)

  def attribute(name: String): String = underlying.attribute(name).orNull

  def setDomProperty(name: String, value: js.Any): Unit = underlying.setProperty(name, value)

  def setStyle(name: String, value: String): Unit = underlying.setStyle(name, value)

  def removeStyle(name: String): Unit = underlying.removeStyle(name)

  def on(eventName: String, handler: js.Function1[UiEventHandle, Unit]): Unit =
    underlying.onHandler(eventName)(event => handler(new UiEventHandle(event)))

  def addDisposable(disposable: DisposableHandle): Unit =
    underlying.addDisposable(disposableFrom(disposable))

  private def disposableFrom(handle: DisposableHandle): CoreDisposable = handle.underlying
}
