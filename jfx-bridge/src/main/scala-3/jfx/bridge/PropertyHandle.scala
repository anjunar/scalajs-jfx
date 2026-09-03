package jfx.bridge

import jfx.core.state.{Property => CoreProperty, ReadOnlyProperty => CoreReadOnlyProperty}

import scala.scalajs.js

/** The JS projection of `jfx.core.state.ReadOnlyProperty`. Mirrors `contract.ts`'s
  * `ReadOnlyProperty<T>`.
  *
  * `get` and `map` are declared without a parameter list on purpose: a non-native JS class compiles
  * a truly nullary `def` to a JS getter, not a method, so `prop.get` reads as a property from
  * TypeScript exactly as the contract says -- not `prop.get()`.
  */
class ReadOnlyPropertyHandle[T](private[bridge] final val underlying: CoreReadOnlyProperty[T])
    extends js.Object {

  def get: T = underlying.get

  def observe(observer: js.Function1[T, Unit]): DisposableHandle =
    new DisposableHandle(underlying.observe(value => observer(value)))

  def observeWithoutInitial(observer: js.Function1[T, Unit]): DisposableHandle =
    new DisposableHandle(underlying.observeWithoutInitial(value => observer(value)))

  def map[R](transform: js.Function1[T, R]): ReadOnlyPropertyHandle[R] =
    new ReadOnlyPropertyHandle[R](underlying.map(value => transform(value)))
}

/** The JS projection of `jfx.core.state.Property`. Mirrors `contract.ts`'s `Property<T>`. */
final class PropertyHandle[T](private[bridge] final val underlyingProperty: CoreProperty[T])
    extends ReadOnlyPropertyHandle[T](underlyingProperty) {

  def set(value: T): Unit = underlyingProperty.set(value)

  def setAlways(value: T): Unit = underlyingProperty.setAlways(value)

  def reset(): Unit = underlyingProperty.reset()

  def isDirty: Boolean = underlyingProperty.isDirty
}
