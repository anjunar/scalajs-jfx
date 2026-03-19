package jfx.state

import org.scalajs.dom.console

import scala.scalajs.js

class Property[T](var value: T) extends ReadOnlyProperty[T] {
  private val listeners = js.Array[(T) => Unit]()

  override def get: T = value

  def set(newValue: T) : Unit = {
    if (newValue == value) return
    value = newValue
    listeners.toList.foreach { it => it(newValue) }
  }

  override def observe(listener: (T) => Unit): Disposable = {
    listeners += listener
    listener(value)

    if (listeners.size > 100) {
      console.warn("Too many listeners on ${this::class.simpleName} : ${listeners.size}")
    }

    () => listeners -= listener
  }

}
