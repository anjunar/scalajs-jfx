package jfx.forms.validators

trait Validator[-V] {
  def validate(value: V): Option[String]
}
