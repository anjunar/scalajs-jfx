package jfx.forms

trait FormController {

  def prefix: String

  def register(field: Control[?]): Unit

  def unregister(field: Control[?]): Unit

  def validateBindings(): Seq[String]

  def setErrorResponses(responses: Seq[ErrorResponse]): Unit

  def clearErrors(): Unit

  def resetInteractionState(): Unit

}
