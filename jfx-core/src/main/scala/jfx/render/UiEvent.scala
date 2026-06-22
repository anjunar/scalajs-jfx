package jfx.render

trait UiEvent {
  def raw: Any
  def preventDefault(): Unit
  def stopPropagation(): Unit
}
