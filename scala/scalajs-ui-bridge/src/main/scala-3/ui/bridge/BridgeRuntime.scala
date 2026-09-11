package ui.bridge

import scala.scalajs.js.annotation.JSExportTopLevel

/** The bridge's only export. The npm package entry point installs it into core automatically:
  *
  * {{{
  * import "@anjunar/scalajs-ui-bridge";
  * }}}
  *
  * Registering the library components here rather than in [[UiRuntimeBridge]] itself keeps that
  * class a pure projection of the contract -- no side effects hiding in its constructor -- and
  * keeps the registration a one-time, load-time fact instead of something every
  * `new UiRuntimeBridge()` would repeat.
  */
object BridgeRuntime {
  ComponentRegistry.register("vbox", VBoxFactory)
  ComponentRegistry.register("hbox", HBoxFactory)
  ComponentRegistry.register("button", ButtonFactory)
  ComponentRegistry.register("drawer", DrawerFactory)
  ComponentRegistry.register("drawer-navigation", DrawerNavigationFactory)
  ComponentRegistry.register("drawer-content", DrawerContentFactory)

  // Step 7 of JAVASCRIPT_API.md §9: the i18n facade. `ui.core.i18n` lives in `scalajs-ui-core` itself
  // (§3), so -- unlike router/controls/viewport/forms/editor below -- registering it here adds no
  // new module edge and no reachability cost beyond what `scalajs-ui-core` already pays.
  ComponentRegistry.register("i18n-provider", I18nProviderFactory)

  // Step 5 of JAVASCRIPT_API.md §9. Registering these here is the point at which `ui.router`
  // becomes reachable from `bridgeRuntime`'s initializer -- a reachability anchor no DCE can
  // resolve, so it lands in the bundle of every consumer, including one that imports only
  // `@anjunar/scalajs-ui-core`. The measured price is in §14; it is the accepted consequence of
  // "one linked runtime artifact" (CLAUDE_REVIEW_3.md §2.2), not a regression.
  ComponentRegistry.register("router", RouterFactory)
  ComponentRegistry.register("router-outlet", RouterOutletFactory)
  ComponentRegistry.register("router-link", RouterLinkFactory)

  // Step 6 of JAVASCRIPT_API.md §9: the controls facade. Same reachability story as the router
  // block above -- these entries anchor `ui.control` into `bridgeRuntime`'s initializer, so a
  // consumer that imports only `@anjunar/scalajs-ui-core` still links them. Measured price on the one
  // artifact is in §14. The `scalajs-ui-controls -> scalajs-ui-viewport` edge is `test->compile` and does not
  // cross into this link (build.sbt).
  ComponentRegistry.register("tabs", TabsFactory)
  ComponentRegistry.register("carousel", CarouselFactory)
  ComponentRegistry.register("table-view", TableViewFactory)
  ComponentRegistry.register("data-grid", DataGridFactory)
  ComponentRegistry.register("virtual-list-view", VirtualListFactory)

  // Step 7 of JAVASCRIPT_API.md §9: the viewport facade. Same reachability story as the two blocks
  // above -- these entries anchor `ui.viewport` into `bridgeRuntime`'s initializer, so a consumer
  // that imports only `@anjunar/scalajs-ui-core` still links them. Measured price on the one artifact is in
  // §14.
  ComponentRegistry.register("viewport", ViewportFactory)
  ComponentRegistry.register("window", WindowFactory)
  ComponentRegistry.register("overlay", OverlayFactory)
  ComponentRegistry.register("notification", NotificationFactory)

  // Step 6 of JAVASCRIPT_API.md §9 ("Komponentenregistratur auffüllen"), the forms half -- the
  // schema decision itself was step 5. Same reachability story as the three blocks
  // above -- these entries anchor `ui.forms` into `bridgeRuntime`'s initializer, so a consumer that
  // imports only `@anjunar/scalajs-ui-core` still links them. `FormFactories.scala` carries the design note
  // for why `form`/`sub-form` cannot just be `ui.forms.Form`/`SubForm` registered directly, the way
  // the other nine entries are.
  ComponentRegistry.register("form", FormFactory)
  ComponentRegistry.register("sub-form", SubFormFactory)
  ComponentRegistry.register("input", InputFactory)
  ComponentRegistry.register("input-container", InputContainerFactory)
  ComponentRegistry.register("field-set", FieldSetFactory)
  ComponentRegistry.register("array-form", ArrayFormFactory)
  ComponentRegistry.register("combo-box", ComboBoxFactory)
  ComponentRegistry.register("image-cropper", ImageCropperFactory)

  // Step 6 of JAVASCRIPT_API.md §9, the editor half -- FINAL.md Priorität 4 ("scalajs-ui-editor
  // veröffentlichen oder bewusst ausklammern") is what gated this one, not the bridge's own
  // progress (npm-Modularisierung Lauf 7). Same reachability story as the four blocks above --
  // this entry anchors `ui.editor` into `bridgeRuntime`'s initializer, so a consumer that imports
  // only `@anjunar/scalajs-ui-core` still links it.
  ComponentRegistry.register("editor", EditorFactory)

  @JSExportTopLevel("bridgeRuntime")
  val bridgeRuntime: UiRuntimeBridge = new UiRuntimeBridge()
}
