package jfx.state

import scala.collection.mutable

trait ReadOnlyProperty[A] {
  def get: A
  def observe(listener: A => Unit): Disposable

  def map[B](f: A => B): ReadOnlyProperty[B] = {
    val source = this
    new ReadOnlyProperty[B] {
      def get: B = f(source.get)
      def observe(listener: B => Unit): Disposable =
        source.observe(value => listener(f(value)))
    }
  }
}

final class Property[A](initial: A) extends ReadOnlyProperty[A] {
  private var value = initial
  private val listeners = mutable.ArrayBuffer.empty[A => Unit]

  def get: A = value

  def set(next: A): Unit =
    if (value != next) {
      value = next
      listeners.toSeq.foreach(_(value))
    }

  def update(f: A => A): Unit = set(f(value))

  def observe(listener: A => Unit): Disposable = {
    listeners += listener
    Disposable {
      val idx = listeners.indexOf(listener)
      if (idx >= 0) listeners.remove(idx)
    }
  }
}
