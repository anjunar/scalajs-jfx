package jfx.domain

import jfx.core.macros.property
import jfx.core.state.{Property, PropertyAccess}

import scala.scalajs.js

class Media(
    name: Property[String] = Property(""),
    contentType: Property[String] = Property(""),
    data: Property[String] = Property(""),
    var thumbnail: Property[Thumbnail] = Property[Thumbnail](null)
) extends Thumbnail(name, contentType, data) {

  override def properties: js.Array[PropertyAccess[Thumbnail, ?]] = Media.properties.asInstanceOf[js.Array[PropertyAccess[Thumbnail, ?]]]
}

object Media {
  val properties: js.Array[PropertyAccess[Media, ?]] = js.Array(
    property(_.name),
    property(_.contentType),
    property(_.data),
    property(_.thumbnail)
  )
}
