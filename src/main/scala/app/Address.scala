package app

import jfx.core.macros.property
import jfx.core.state.{Property, PropertyAccess}
import jfx.form.Model

import scala.scalajs.js

class Address(var street: Property[String], var city: Property[String]) extends Model[Address] {
  override def properties: js.Array[PropertyAccess[Address, ?]] = Address.properties
}

object Address {
  val properties: js.Array[PropertyAccess[Address, ?]] = js.Array(
    property(_.street),
    property(_.city)
  )
}
