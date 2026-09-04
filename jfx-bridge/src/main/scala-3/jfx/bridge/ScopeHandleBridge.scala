package jfx.bridge

import jfx.core.component.AbstractComponent
import jfx.core.document.DocumentHead
import jfx.core.dsl.DslLayer
import jfx.core.layout.{Condition, Head, TextComponent}
import jfx.core.render.Cursor
import jfx.core.state.{ReadOnlyProperty => CoreReadOnlyProperty}
import jfx.core.statement.Foreach

import scala.concurrent.ExecutionContext
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

/** The JS projection of the pair `(AbstractComponent, Cursor)` that Scala threads through
  * `using AbstractComponent, Cursor`. Mirrors `contract.ts`'s `ScopeHandle`.
  *
  * Every method here follows the same shape: install `parent`/`cursor` as givens, call the matching
  * `jfx-core` DSL entry point, and hand the JS callback a fresh `ScopeHandleBridge` built from
  * whatever component and cursor that entry point produced. `DslLayer.child` and friends run `body`
  * themselves and unmount a half-built child if it throws (see `contract.ts`'s note on `child`) --
  * this class never mounts in two steps, so that guarantee survives the crossing.
  */
final class ScopeHandleBridge(
    private[bridge] final val parent: AbstractComponent,
    private[bridge] final val cursor: Cursor
) extends js.Object {

  def isBrowser: Boolean = cursor.isBrowser

  def isHydrating: Boolean = cursor.isHydrating

  def child(
      tagName: String,
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  ): ComponentHandleBridge = {
    given AbstractComponent = parent
    given Cursor            = cursor

    val mounted = DslLayer.child(new GenericElement(tagName)) {
      val self        = summon[GenericElement]
      val childCursor = summon[Cursor]
      body(new ComponentHandleBridge(self), new ScopeHandleBridge(self, childCursor))
    }

    new ComponentHandleBridge(mounted)
  }

  def text(value: js.Any): ComponentHandleBridge = {
    given AbstractComponent = parent
    given Cursor            = cursor

    val mounted = DslLayer.child(TextComponent.bind(ReactiveBridge.asProperty[String](value))) {}
    new ComponentHandleBridge(mounted)
  }

  /** Mounts `jfx.core.layout.Head`, not a `GenericElement("head")` -- the one element that wires
    * itself up to the surrounding [[DocumentHead]] once mounted (`Head.afterCompose`), which is
    * what `documentHead()` calls elsewhere in the tree rely on.
    */
  def head(
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  ): ComponentHandleBridge = {
    given AbstractComponent = parent
    given Cursor            = cursor

    val mounted = Head.head {
      val self        = summon[Head]
      val childCursor = summon[Cursor]
      body(new ComponentHandleBridge(self), new ScopeHandleBridge(self, childCursor))
    }

    new ComponentHandleBridge(mounted)
  }

  /** The request-scoped head registry, or `null` outside a document tree that mounted a [[head]]
    * element. Mirrors `contract.ts`'s `ScopeHandle.documentHead`.
    */
  def documentHead(): DocumentHeadHandleBridge = {
    given AbstractComponent = parent

    DocumentHead.current.map(new DocumentHeadHandleBridge(_)).orNull
  }

  def when(active: JsReadOnlyProperty[Boolean], body: js.Function1[ScopeHandleBridge, Unit]): Unit = {
    given AbstractComponent = parent
    given Cursor            = cursor

    Condition.when(ReactiveBridge.wrap(active)) {
      val self        = summon[AbstractComponent]
      val childCursor = summon[Cursor]
      body(new ScopeHandleBridge(self, childCursor))
    }
  }

  def forEach(
      items: JsReadOnlyProperty[js.Array[js.Any]],
      body: js.Function3[js.Any, Int, ScopeHandleBridge, Unit]
  ): Unit = {
    given AbstractComponent = parent
    given Cursor            = cursor

    val itemsAsSeq: CoreReadOnlyProperty[Seq[js.Any]] =
      ReactiveBridge.wrap(items).map(_.toSeq)

    Foreach.foreachIndexed(itemsAsSeq) { (value, index) =>
      val self        = summon[AbstractComponent]
      val childCursor = summon[Cursor]
      body(value, index, new ScopeHandleBridge(self, childCursor))
    }
  }

  def fetch(
      load: js.Function0[js.Promise[js.Any]],
      onLoaded: js.Function2[js.Any, ScopeHandleBridge, Unit],
      onFailed: js.Function2[js.Any, ScopeHandleBridge, Unit]
  ): Unit = {
    given AbstractComponent = parent
    given Cursor            = cursor
    given ExecutionContext  = ExecutionContext.global

    jfx.core.layout.FetchComponent.fetch(() => load().toFuture) { value =>
      val self        = summon[AbstractComponent]
      val childCursor = summon[Cursor]
      onLoaded(value, new ScopeHandleBridge(self, childCursor))
    } { error =>
      val self        = summon[AbstractComponent]
      val childCursor = summon[Cursor]
      onFailed(BridgeErrors.toJs(error), new ScopeHandleBridge(self, childCursor))
    }
  }

  def component(
      name: String,
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  ): ComponentHandleBridge = {
    given AbstractComponent = parent
    given Cursor            = cursor

    val factory = ComponentRegistry.get(name).getOrElse(
      throw new IllegalArgumentException(s"No component is registered under the name '$name'.")
    )

    new ComponentHandleBridge(factory.mount(options, body))
  }
}
