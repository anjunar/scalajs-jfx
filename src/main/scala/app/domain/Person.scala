package app.domain

import jfx.domain.Media
import jfx.core.macros.property
import jfx.core.state.{ListProperty, Property, PropertyAccess}
import jfx.form.Model

import scala.scalajs.js

class Person(var firstName: Property[String] = new Property[String](""),
             var lastName: Property[String] = new Property[String](""),
             var team: ListProperty[String] = new ListProperty[String](),
             var address: Property[Address] = new Property[Address](null),
             var emails: ListProperty[Email] = new ListProperty[Email](),
             var media: Property[Media] = new Property[Media](null)) extends Model[Person] {
  override def properties: js.Array[PropertyAccess[Person, ?]] = Person.properties
}


object Person {

  val properties: js.Array[PropertyAccess[Person, ?]] = js.Array(
    property(_.firstName),
    property(_.lastName),
    property(_.team),
    property(_.address),
    property(_.emails),
    property(_.media)
  )

}
