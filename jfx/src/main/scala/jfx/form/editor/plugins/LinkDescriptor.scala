package jfx.form.editor.plugins

import jfx.core.state.Property

import scala.scalajs.js

final case class LinkDescriptor(
    val href: Property[String] = Property(""),
    val title: Property[String] = Property("")
)

object LinkDescriptor {

}
