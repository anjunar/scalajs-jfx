package jfx.bridge

import scala.scalajs.js.annotation.JSExportTopLevel

/** The bridge's only export. `npm/jfx/README.md` already documents the consumer side of this:
  *
  * {{{
  * import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge";
  * installRuntime(bridgeRuntime);
  * }}}
  *
  * Registering the library components here rather than in [[JfxRuntimeBridge]] itself keeps that
  * class a pure projection of the contract -- no side effects hiding in its constructor -- and keeps
  * the registration a one-time, load-time fact instead of something every `new JfxRuntimeBridge()`
  * would repeat.
  */
object BridgeRuntime {
  ComponentRegistry.register("vbox", VBoxFactory)
  ComponentRegistry.register("hbox", HBoxFactory)
  ComponentRegistry.register("button", ButtonFactory)

  @JSExportTopLevel("bridgeRuntime")
  val bridgeRuntime: JfxRuntimeBridge = new JfxRuntimeBridge()
}
