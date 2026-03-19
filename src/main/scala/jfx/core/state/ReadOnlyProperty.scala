package jfx.core.state

import scala.scalajs.js

trait ReadOnlyProperty[V] {

  def get : V

  def observe(observer : V => Unit) : Disposable



}
