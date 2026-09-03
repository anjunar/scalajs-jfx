package jfx.bridge

import jfx.core.render.{UiEvent => CoreUiEvent}
import org.scalajs.dom

import scala.scalajs.js

/** The JS projection of `jfx.core.render.UiEvent`. Mirrors `contract.ts`'s `UiEvent`.
  *
  * Scala's `UiEvent` only carries `raw: Any`, `preventDefault()` and `stopPropagation()` -- `type`,
  * `target` and `native` are read off the underlying DOM event when there is one. During SSR no
  * handler ever fires, so that case does not arise in practice; the fallbacks exist so this class
  * has a total mapping regardless.
  */
final class UiEventHandle(private val underlying: CoreUiEvent) extends js.Object {

  private def domEvent: Option[dom.Event] = underlying.raw match {
    case event: dom.Event => Some(event)
    case _                => None
  }

  def `type`: String = domEvent.map(_.`type`).getOrElse("")

  def target: js.Any = domEvent match {
    case Some(event) => event.target
    case None         => js.undefined
  }

  def preventDefault(): Unit = underlying.preventDefault()

  def stopPropagation(): Unit = underlying.stopPropagation()

  def native: dom.Event = domEvent match {
    case Some(event) => event
    case None         => null.asInstanceOf[dom.Event]
  }
}
