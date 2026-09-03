package jfx.bridge

import jfx.core.component.AbstractCustomComponent
import jfx.core.render.Cursor

import scala.scalajs.js

/** The one component every `Build` mounts under.
 *
 * `contract.ts`'s `Build = (scope: ScopeHandle) => void` hands TypeScript a scope, not a component
 * to mount -- `mount`, `hydrate` and `renderToString` all take a bare `build`, the same way
 * `demo/statePage.ts` calls `vbox(() => { ... })` directly with nothing wrapping it. `Runtime.mount`
 * still needs an `AbstractComponent` to start from, so this plays exactly the role `Condition`,
 * `Foreach` and `FetchComponent` already play at every other nesting level: a virtual, invisible
 * component whose only job is to exist so a cursor has something to hang off of.
 *
 * `compose`'s `cursor` parameter is already the resolved content cursor -- `Runtime.mountWithCursor`
 * works that out before calling `compose` at all -- so unlike those three, there is nothing here to
 * resolve a second time.
 */
private[bridge] final class BridgeRoot(build: js.Function1[ScopeHandleBridge, Unit])
    extends AbstractCustomComponent {

  override def compose(cursor: Cursor): Unit =
    build(new ScopeHandleBridge(this, cursor))
}
