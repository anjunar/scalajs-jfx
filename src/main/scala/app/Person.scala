package app

import jfx.core.macros.property
import jfx.core.state.{Property, PropertyAccess}
import jfx.form.Model

import scala.scalajs.js

class Person(var firstName: Property[String], var lastName: Property[String]) extends Model[Person] {
  override def properties: js.Array[PropertyAccess[Person, ?]] = Person.properties
}

object Person {
  
  val properties: js.Array[PropertyAccess[Person, ?]] = js.Array(
    property(_.firstName),
    property(_.lastName)
  )

}
