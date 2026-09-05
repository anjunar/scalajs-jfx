package jfx.bridge

import jfx.core.state.{Disposable => CoreDisposable, ReadOnlyProperty => CoreReadOnlyProperty}

import scala.scalajs.js

/** The facade for a `ReadOnlyProperty<T>` TypeScript hands *into* Scala -- as `when`'s `active`, as
  * `forEach`'s `items`, as an option value resolved through [[ReactiveBridge.asProperty]]. Native,
  * because Scala never constructs one: every `ReadOnlyProperty<T>` in a TS consumer's hands
  * originated from [[JfxRuntimeBridge.property]], `.map`, or a library component, and this trait
  * only has to describe its shape, not build it.
  */
@js.native
trait JsReadOnlyProperty[T] extends js.Object {
  def get: T                                                               = js.native
  def observe(observer: js.Function1[T, Unit]): JsDisposable               = js.native
  def observeWithoutInitial(observer: js.Function1[T, Unit]): JsDisposable = js.native
}

/** A constant lifted into `ReadOnlyProperty[T]`, for the non-reactive half of `Reactive<T>`. */
final class ConstantProperty[T](value: T) extends CoreReadOnlyProperty[T] {
  override def get: T = value

  override def observe(observer: T => Unit): CoreDisposable = {
    observer(value)
    CoreDisposable.empty
  }

  override def observeWithoutInitial(observer: T => Unit): CoreDisposable =
    CoreDisposable.empty
}

/** Resolves `Reactive<T> = T | ReadOnlyProperty<T>` at the boundary.
  *
  * Mirrors `dsl.ts`'s own `isProperty`: an object exposing a callable `observe` is treated as a
  * property, anything else as a constant. TypeScript already applies the same duck test for `attr`,
  * `style` and `domProperty`, which resolve `Reactive` without ever crossing into Scala; this is
  * the one place the bridge has to do it itself, because `text()` and component options hand a
  * `Reactive` straight through `ScopeHandle`.
  */
object ReactiveBridge {

  def isObservable(value: js.Any): Boolean =
    value != null &&
      js.typeOf(value) == "object" &&
      js.typeOf(value.asInstanceOf[js.Dynamic].observe) == "function"

  def wrap[T](property: JsReadOnlyProperty[T]): CoreReadOnlyProperty[T] =
    new CoreReadOnlyProperty[T] {
      override def get: T = property.get

      override def observe(observer: T => Unit): CoreDisposable = {
        val handle = property.observe(value => observer(value))
        () => handle.dispose()
      }

      override def observeWithoutInitial(observer: T => Unit): CoreDisposable = {
        val handle = property.observeWithoutInitial(value => observer(value))
        () => handle.dispose()
      }
    }

  def asProperty[T](value: js.Any): CoreReadOnlyProperty[T] =
    if (isObservable(value)) wrap[T](value.asInstanceOf[JsReadOnlyProperty[T]])
    else new ConstantProperty[T](value.asInstanceOf[T])
}
