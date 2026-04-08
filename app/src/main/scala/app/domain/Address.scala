package app.domain

import jfx.core.state.Property

import scala.scalajs.js

class Address(var street: Property[String] = Property(""), var city: Property[String] = Property(""))
