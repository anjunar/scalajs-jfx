package jfx.core.render

trait UiEvent {
  def raw: Any
  def preventDefault(): Unit
  def stopPropagation(): Unit
}
