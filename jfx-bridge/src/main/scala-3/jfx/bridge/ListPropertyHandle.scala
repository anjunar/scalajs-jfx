package jfx.bridge

import jfx.core.state.{ListProperty => CoreListProperty}

import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

/** The JS projection of `jfx.core.state.ListProperty`. Mirrors `contract.ts`'s `ListProperty<T>`.
  *
  * `ListProperty[V]` is already a `ReadOnlyProperty[js.Array[V]]`, so this extends
  * [[ReadOnlyPropertyHandle]] the same way the TypeScript interface extends
  * `ReadOnlyProperty<readonly T[]>`.
  */
final class ListPropertyHandle[T](private[bridge] final val underlyingList: CoreListProperty[T])
    extends ReadOnlyPropertyHandle[js.Array[T]](underlyingList) {

  def setAll(values: js.Array[T]): Unit = underlyingList.setAll(values.toSeq)

  def add(value: T): Unit = underlyingList.addOne(value)

  def insert(index: Int, value: T): Unit = underlyingList.insert(index, value)

  def removeAt(index: Int): Unit = underlyingList.remove(index)

  def clear(): Unit = underlyingList.clear()

  def size: Int = underlyingList.length
}
