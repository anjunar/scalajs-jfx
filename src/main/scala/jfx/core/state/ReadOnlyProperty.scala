package jfx.core.state

import scala.scalajs.js

trait ReadOnlyProperty[V] {

  def get : V

  def observe(observer : V => Unit) : Disposable

  def map[T](transform: V => T): ReadOnlyProperty[T] = {
    val source = this
    new ReadOnlyProperty[T] {
      override def get: T =
        transform(source.get)

      override def observe(observer: T => Unit): Disposable =
        source.observe(value => observer(transform(value)))
    }
  }



}
