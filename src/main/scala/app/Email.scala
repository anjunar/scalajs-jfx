package app

import jfx.core.macros.property
import jfx.core.state.{Property, PropertyAccess}
import jfx.form.Model

import scala.scalajs.js

class Email(var value : Property[String] = new Property("")) extends Model[Email]{
  override def properties: js.Array[PropertyAccess[Email, ?]] = Email.properties
}

object Email {

  val properties: js.Array[PropertyAccess[Email, ?]] = js.Array(
    property(_.value)
  )

}
