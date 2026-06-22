package jfx.render

import org.scalajs.dom

final class DomUiEvent(private val event: dom.Event) extends UiEvent {
  def raw: Any = event
  def preventDefault(): Unit = event.preventDefault()
  def stopPropagation(): Unit = event.stopPropagation()
}
