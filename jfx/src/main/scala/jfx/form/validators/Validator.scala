package jfx.form.validators

trait Validator[-V] {

  def validate(value: V): Option[String]

}
