package jfx.forms

trait FormController {

  def prefix: String

  def register(field: Control[?]): Unit

  def unregister(field: Control[?]): Unit

  def clearErrors(): Unit

  def resetInteractionState(): Unit

}
