package jfx.forms

trait Placeholder {

  def placeholder(value: String): Unit

}

object Placeholder {
  def placeholder(value: String)(using input: Placeholder): Unit =
    input.placeholder(value)

}
