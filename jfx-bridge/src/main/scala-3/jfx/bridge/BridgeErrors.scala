package jfx.bridge

import scala.scalajs.js

/** Unwraps a failed `Future` back to the value TypeScript should see.
  *
  * `js.Promise[T].toFuture` (used by [[ScopeHandleBridge.fetch]] and [[JfxRuntimeBridge.hydrate]])
  * wraps a rejection that was not itself a `Throwable` in `js.JavaScriptException`. Unwrapping it
  * here means `onFailed`/a rejected `renderToString` sees the original JS value -- an `Error`, a
  * string, whatever the loader rejected with -- instead of a Scala exception it never threw.
  */
private[bridge] object BridgeErrors {
  def toJs(error: Throwable): js.Any = error match {
    case js.JavaScriptException(reason) => reason.asInstanceOf[js.Any]
    case other                          => other.getMessage
  }
}
