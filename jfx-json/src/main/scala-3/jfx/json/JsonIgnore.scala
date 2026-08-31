package jfx.json

import scala.annotation.StaticAnnotation

class JsonIgnore(
    val serializable: Boolean = false,
    val deserializable: Boolean = false
) extends StaticAnnotation
