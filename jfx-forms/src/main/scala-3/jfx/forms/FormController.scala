package jfx.forms

import scala.collection.mutable

trait FormController(val prefix: String) {

  def register(field: Control): Unit

}
