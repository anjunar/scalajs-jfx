package jfx.bridge

import scala.util.DynamicVariable

/** Per-render SSR status slot.
  *
  * [[JfxRuntimeBridge.renderToString]] opens one for the duration of the synchronous mount;
  * [[RouterFactory]] binds the mounted router's `responseStatus` into it, so the serialized response
  * can carry a route's own `404`/`500` instead of the blanket `200` the bridge fixed before it knew
  * about routing (see the doc comment on `renderToString`).
  *
  * Client-side `mount` and `hydrate` never open one -- there is no HTTP response there to carry a
  * status. A render with no router leaves the reader at its `200` default.
  */
private[bridge] final class SsrStatus {
  private var reader: () => Int = () => 200

  def bind(read: () => Int): Unit =
    reader = read

  /** Read live: called after `renderToStringAsync` has drained, so an error route that only decided
    * its status during an awaited loader failure is already reflected.
    */
  def get: Int = reader()
}

private[bridge] object SsrStatus {
  private val slot = new DynamicVariable[Option[SsrStatus]](None)

  /** Holds `holder` current for the synchronous execution of `body` -- long enough for
    * `Runtime.mount` to run every component's `compose`, which is where [[RouterFactory]] binds.
    */
  def capture[A](holder: SsrStatus)(body: => A): A =
    slot.withValue(Some(holder))(body)

  def current: Option[SsrStatus] =
    slot.value
}
