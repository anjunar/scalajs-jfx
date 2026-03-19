package jfx.core.state

trait PropertyAccess[T, V] {
  val name: String
  def get(obj: T): Option[V]
  def set(obj: T, value: V): Unit
}