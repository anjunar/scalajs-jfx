package app.domain

import jfx.domain.Media
import jfx.core.state.{ListProperty, Property}

import scala.scalajs.js

class Person(var firstName: Property[String] = new Property[String](""),
             var lastName: Property[String] = new Property[String](""),
             var team: ListProperty[String] = new ListProperty[String](),
             var address: Property[Address] = new Property[Address](null),
             var emails: ListProperty[Email] = new ListProperty[Email](),
             var media: Property[Media] = new Property[Media](null)) 