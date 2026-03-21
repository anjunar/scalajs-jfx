package jfx.domain

import jfx.core.macros.property
import jfx.core.state.{Property, PropertyAccess}
import jfx.form.Model

import scala.scalajs.js

class Thumbnail(
    var name: Property[String] = Property(""),
    var contentType: Property[String] = Property(""),
    var data: Property[String] = Property("")
) extends Model[Thumbnail] {

  override def properties: js.Array[PropertyAccess[Thumbnail, ?]] = Thumbnail.properties
}

object Thumbnail {
  val properties: js.Array[PropertyAccess[Thumbnail, ?]] = js.Array(
    property(_.name),
    property(_.contentType),
    property(_.data)
  )
}
