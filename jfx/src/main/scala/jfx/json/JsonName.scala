package jfx.json

import scala.annotation.StaticAnnotation

/**
 * Annotation to specify a custom JSON field name for serialization/deserialization.
 * 
 * @param value The name to use in JSON instead of the Scala field name
 */
final case class JsonName(value: String) extends StaticAnnotation
