package jfx.bridge

import jfx.core.component.AbstractComponent
import jfx.core.di.Context

/** Per-render SSR status slot.
  *
  * [[BridgeRoot]] provides it through the component context. A [[RouterFactory]] mounted after
  * asynchronous work can therefore still bind its `responseStatus` to the response being rendered.
  * Client-side mounts do not provide a slot, and a render with no router stays at its `200` default.
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
  private val context = Context.create[SsrStatus]("SsrStatus")

  def provide(value: SsrStatus)(using component: AbstractComponent): Unit =
    context.provide(value)

  def current(using component: AbstractComponent): Option[SsrStatus] =
    context.inject
}
