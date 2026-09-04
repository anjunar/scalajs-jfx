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

  // Step 6 of JAVASCRIPT_API.md §9: the controls facade. Same reachability story as the router
  // block above -- these entries anchor `jfx.control` into `bridgeRuntime`'s initializer, so a
  // consumer that imports only `@anjunar/jfx-core` still links them. Measured price on the one
  // artifact is in §14. The `jfx-controls -> jfx-viewport` edge is `test->compile` and does not
  // cross into this link (build.sbt).
  ComponentRegistry.register("tabs", TabsFactory)
  ComponentRegistry.register("carousel", CarouselFactory)
  ComponentRegistry.register("table-view", TableViewFactory)
  ComponentRegistry.register("data-grid", DataGridFactory)
  ComponentRegistry.register("virtual-list-view", VirtualListFactory)

  @JSExportTopLevel("bridgeRuntime")
  val bridgeRuntime: JfxRuntimeBridge = new JfxRuntimeBridge()
}
