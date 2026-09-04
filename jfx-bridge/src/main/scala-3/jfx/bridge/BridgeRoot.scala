package jfx.bridge

import jfx.core.component.AbstractComponent
import jfx.core.document.DocumentHead
import jfx.core.render.Cursor

import scala.scalajs.js

/** The one component every `Build` mounts under.
 *
 * `contract.ts`'s `Build = (scope: ScopeHandle) => void` hands TypeScript a scope, not a component
 * to mount -- `mount`, `hydrate` and `renderToString` all take a bare `build`, the same way
 * `demo/statePage.ts` calls `vbox(() => { ... })` directly with nothing wrapping it. `Runtime.mount`
 * still needs an `AbstractComponent` to start from, so ordinarily (`tagName = ""`) this plays exactly
 * the role `Condition`, `Foreach` and `FetchComponent` already play at every other nesting level: a
 * virtual, invisible component whose only job is to exist so a cursor has something to hang off of.
 *
 * `tagName = "html"` is the one exception, used for a whole-document render/hydration
 * (`JfxRuntimeBridge.renderToString`'s `options.document`, `.hydrate`'s `Document` branch). A virtual
 * root cannot stand for `<html>`: `HydratingCursor.root(document, ...)` sets its very first expected
 * node to `document.documentElement` itself -- the real element, not a comment marking an invisible
 * wrapper's boundary -- exactly what `component.isVirtual == false` here claims via
 * `cursor.claimElement("html")`. `build` must then compose the document's `head()`/body content
 * directly, with no enclosing `html(...)` of its own; see `jfx-demo`'s `app/document.ts`.
 *
 * `compose`'s `cursor` parameter is already the resolved content cursor -- `Runtime.mountWithCursor`
 * works that out before calling `compose` at all -- so unlike those three, there is nothing here to
 * resolve a second time.
 *
 * It also provides a fresh [[DocumentHead]], the same way `app.AppDocument` provides one on the
 * Scala side -- one instance per `mount`/`hydrate`/`renderToString` call, matching `DocumentHead`'s
 * own "one instance per request" contract. `ScopeHandle.documentHead()` and `.head(...)` reach it
 * from anywhere in the tree; a build that never calls either leaves it unused (`HeadSink.Discarding`
 * by default), so this costs nothing for a `Build` that isn't a full document.
 */
private[bridge] final class BridgeRoot(
    build: js.Function1[ScopeHandleBridge, Unit],
    val tagName: String = ""
) extends AbstractComponent {

  override def compose(cursor: Cursor): Unit = {
    DocumentHead.provide(new DocumentHead())(using this)
    build(new ScopeHandleBridge(this, cursor))
  }
}
