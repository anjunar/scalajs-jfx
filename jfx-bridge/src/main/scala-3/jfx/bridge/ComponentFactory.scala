package jfx.bridge

import jfx.core.component.AbstractComponent
import jfx.core.render.Cursor

import scala.scalajs.js

/** Mounts one library component by name.
  *
  * `options` is whatever `ScopeHandle.component`'s caller passed -- a raw `Record<string, unknown>`
  * from TypeScript, unchecked until a factory reads a key out of it. `body` is the JS closure to run
  * once the component and its content cursor exist, exactly like [[ScopeHandleBridge.child]]'s.
  */
private[bridge] trait ComponentFactory {
  def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent
}

/** Name -> class. Mirrors `jfx-bridge`'s share of §3 in JAVASCRIPT_API.md: the registry lives here,
  * the typed wrappers live in `dsl.ts`.
  *
  * A `Map` in an `object` looks like the requestbound state ARCHITECTURE.md §5 forbids, but it is
  * not: every entry is registered once at module load, from constant factories, and never touched
  * again. That is exactly the "constants, pure functions and factories" §5 allows an `object` to
  * hold.
  */
private[bridge] object ComponentRegistry {
  private val factories = scala.collection.mutable.Map.empty[String, ComponentFactory]

  def register(name: String, factory: ComponentFactory): Unit =
    factories(name) = factory

  def get(name: String): Option[ComponentFactory] =
    factories.get(name)
}
