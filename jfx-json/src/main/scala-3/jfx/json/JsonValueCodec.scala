package jfx.json

import java.util.UUID
import scala.scalajs.js

private[json] object JsonValueCodec {

  def serializePrimitive(value: Any, typeName: String): js.Any =
    typeName match {
      case "java.util.UUID" =>
        value.asInstanceOf[UUID].toString
      case "scala.Int" | "int" =>
        value.asInstanceOf[Int].toDouble
      case "scala.Double" | "double" =>
        value.asInstanceOf[Double]
      case "scala.Float" | "float" =>
        value.asInstanceOf[Float].toDouble
      case "scala.Long" | "long" =>
        value.asInstanceOf[Long].toDouble
      case "scala.Short" | "short" =>
        value.asInstanceOf[Short].toDouble
      case "scala.Byte" | "byte" =>
        value.asInstanceOf[Byte].toDouble
      case "scala.Boolean" | "boolean" =>
        value.asInstanceOf[Boolean]
      case "scala.Char" | "char" =>
        value.toString
      case _ =>
        value.toString
    }

  def deserializePrimitive(value: js.Any, typeName: String): Any =
    typeName match {
      case "scala.Int" | "int"         => value.asInstanceOf[Double].toInt
      case "scala.Double" | "double"   => value.asInstanceOf[Double]
      case "scala.Float" | "float"     => value.asInstanceOf[Double].toFloat
      case "scala.Long" | "long"       => value.asInstanceOf[Double].toLong
      case "scala.Short" | "short"     => value.asInstanceOf[Double].toShort
      case "scala.Byte" | "byte"       => value.asInstanceOf[Double].toByte
      case "scala.Boolean" | "boolean" => value.asInstanceOf[Boolean]
      case "scala.Char" | "char"       =>
        value.toString.headOption.getOrElse('\u0000')
      case "java.util.UUID" =>
        UUID.fromString(uuidValue(value.toString))
      case _ =>
        value
    }

  def asObject(value: js.Any): js.Dictionary[js.Any] =
    if (
      value != null &&
      !js.isUndefined(value) &&
      !js.Array.isArray(value) &&
      js.typeOf(value) == "object"
    ) value.asInstanceOf[js.Dictionary[js.Any]]
    else throw new IllegalArgumentException(s"Expected JSON object, got ${js.typeOf(value)}")

  def asArray(value: js.Any): js.Array[js.Any] =
    if (js.Array.isArray(value)) value.asInstanceOf[js.Array[js.Any]]
    else throw new IllegalArgumentException(s"Expected JSON array, got ${js.typeOf(value)}")

  private def uuidValue(raw: String): String = {
    val value = Option(raw).getOrElse("").trim
    if (value.contains("/")) value.split('/').lastOption.getOrElse(value)
    else value
  }
}
