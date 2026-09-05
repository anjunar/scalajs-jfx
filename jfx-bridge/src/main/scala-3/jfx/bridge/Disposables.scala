package jfx.bridge

import jfx.core.state.{Disposable => CoreDisposable}

import scala.scalajs.js

/** The JS projection of `jfx.core.state.Disposable`. Constructed on the Scala side, handed to
  * TypeScript; mirrors `contract.ts`'s `Disposable`.
  */
final class DisposableHandle(private[bridge] final val underlying: CoreDisposable)
    extends js.Object {
  def dispose(): Unit = underlying.dispose()
}

/** The facade for a `Disposable` TypeScript hands *back* to Scala -- today only as the return value
  * of a `JsReadOnlyProperty`'s own `observe`. Native, because Scala never constructs one.
  */
@js.native
trait JsDisposable extends js.Object {
  def dispose(): Unit = js.native
}
