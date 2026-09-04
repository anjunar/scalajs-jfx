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

  // Step 5 of JAVASCRIPT_API.md §9. Registering these here is the point at which `jfx.router`
  // becomes reachable from `bridgeRuntime`'s initializer -- a reachability anchor no DCE can
  // resolve, so it lands in the bundle of every consumer, including one that imports only
  // `@anjunar/jfx-core`. The measured price is in §14; it is the accepted consequence of
  // "one linked runtime artifact" (CLAUDE_REVIEW_3.md §2.2), not a regression.
  ComponentRegistry.register("router", RouterFactory)
  ComponentRegistry.register("router-outlet", RouterOutletFactory)
  ComponentRegistry.register("router-link", RouterLinkFactory)

  @JSExportTopLevel("bridgeRuntime")
  val bridgeRuntime: JfxRuntimeBridge = new JfxRuntimeBridge()
}
