package jfx.bridge

import jfx.core.component.AbstractComponent
import jfx.core.render.Cursor
import jfx.core.state.Disposable
import jfx.viewport.{Overlay, Viewport, Window}

import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

/** Step 7 of JAVASCRIPT_API.md §9: the viewport facade.
  *
  * The trigger from CLAUDE_REVIEW_3.md §5 was "`jfx-bridge` registers `viewport`, `window`,
  * `overlay`, `notification` from `jfx.viewport`". All four are registry entries, the same shape as
  * the router and controls facades -- there is no separate imperative API. `window` and `notification`
  * can still be triggered exactly like their Scala counterparts, though: `contract.ts`'s
  * `component(name, options, body)` called *inside* a TypeScript `onClick` mounts there, at click
  * time, because `scope.ts`'s `capture`/`withScope` restore the ambient scope the handler closed
  * over -- the same as `Viewport.notify`/`Viewport.addWindow` reading their ambient `given
  * AbstractComponent` from an `onClick` body in Scala. A `when(condition, ...)` toggle works too, for
  * a window that should stay open only as long as some reactive state says so.
  *
  * [[WindowFactory]] and [[NotificationFactory]] deliberately do *not* mount anything of their own at
  * the call site (no `DslLayer.child`, unlike [[OverlayFactory]]) -- and that is not a style choice,
  * it is required. `mount`'s ambient `Cursor` is whatever `ScopeHandleBridge` was captured by the
  * enclosing `on(...)`/`capture()` at the time the handler was *registered*, during the initial
  * render. Calling `notify(...)` straight from `onClick`, with no reactive gate in between, replays
  * that exact captured cursor at click time -- and if hydration has since finished, it is a spent
  * `HydratingCursor` with nothing left to claim, so any `DslLayer.child` against it throws "There is
  * no further DOM node" (found the hard way: a demo page calling `notify()` from a bare `onClick`
  * hydrated fine and then faulted on the very first click). `Condition.when`'s reactive branches don't
  * have this problem -- each activation gets a fresh, live cursor of `Condition`'s own making, which
  * is why `when(open) { floatingWindow(...) }` works with no special-casing. A bare `onClick` has no
  * such reactive branch to hand out a fresh one.
  *
  * The fix is to never need a cursor at the call site at all: call `Viewport.addWindow`/
  * `Viewport.notify` directly against `parent` (no `Cursor`, by their own signatures), and hang the
  * "close if unmounted early" disposable off `parent` too, instead of a purpose-built wrapper
  * component. `parent` is already exactly the right lifetime in both idioms -- the branch host
  * `Condition.when` disposes when its condition flips back, or the button itself when its routed page
  * is torn down -- so no wrapper is needed to own it. The actual visual mount (a `Window`/`Notification`
  * child) happens later and elsewhere, inside `Viewport.compose`'s own `Foreach.foreach(windows)` /
  * `Foreach.foreach(notifications)`, which has been reactively inserting into an already-hydrated tree
  * since long before this facade existed (`todosPage`'s `forEach` over live-added items is the same
  * mechanism) -- there is no stale cursor there to begin with.
  *
  * Reactive titles/messages (`Viewport.notify[T](...)(using TextValue[T])`,
  * `WindowConf#title_=(ReadOnlyProperty[String])`) are not projected -- nothing in this repository's
  * own Scala usage (`WindowPage.scala`, `ViewportPage.scala`) passes anything but an already-resolved
  * `String` to either. `onClick` on a window (bring-to-front is unconditional; this would be an
  * *additional* hook) and the window handle's `touchWindow`/`isActive` readback are deferred for the
  * same reason `ControlFactories` deferred its imperative handles: no consumer of this facade needs
  * them yet, and each has an obvious trigger to add later if one shows up.
  */
private[bridge] object ViewportFactories {

  def notificationKind(value: String): Viewport.NotificationKind =
    value match {
      case "success" => Viewport.NotificationKind.Success
      case "warning" => Viewport.NotificationKind.Warning
      case "error"   => Viewport.NotificationKind.Error
      case _         => Viewport.NotificationKind.Info
    }
}

/** `viewport` -- the root host for windows, overlays and notifications. Mirrors `Viewport.viewport`. */
private[bridge] object ViewportFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent =
    Viewport.viewport {
      body(new ComponentHandleBridge(summon[AbstractComponent]), new ScopeHandleBridge(summon[AbstractComponent], summon[Cursor]))
    }
}

/** `overlay` -- an anchor-following surface, present in the tree for as long as it should stay open.
  * Mirrors `Overlay.overlay`. Always needs a reactive gate above it (`when(...)`), the same as
  * `jfx.forms.ComboBox`'s own dropdown -- it mounts a real, visible `div` at the call site, so (unlike
  * `window`/`notification` below) it needs the call site's cursor to be genuinely live.
  */
private[bridge] object OverlayFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent = {
    val widthPx = options.get("widthPx").map(ControlFactories.dbl)

    Overlay.overlay(widthPx) {
      val self: Overlay = summon[Overlay]
      body(new ComponentHandleBridge(self), new ScopeHandleBridge(self, summon[Cursor]))
    }
  }
}

/** `window` -- a movable island in the viewport, open for as long as it is mounted. Mounts nothing at
  * the call site; see the file-level doc comment for why.
  */
private[bridge] object WindowFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent = {
    val title    = ControlFactories.str(options("title"))
    val widthPx  = options.get("widthPx").map(ControlFactories.int).getOrElse(520)
    val heightPx = options.get("heightPx").map(ControlFactories.int).getOrElse(360)
    val onClose  = options.get("onClose").map(_.asInstanceOf[js.Function0[Unit]]).orUndefined

    val conf = Viewport.WindowConf(
      title,
      widthPx,
      heightPx,
      onClose = onClose.toOption.map(cb => (_: Window) => cb())
    ) {
      body(new ComponentHandleBridge(summon[AbstractComponent]), new ScopeHandleBridge(summon[AbstractComponent], summon[Cursor]))
    }

    Viewport.addWindow(conf)(using parent)
    parent.addDisposable(Disposable(Viewport.closeWindow(conf)))
    parent
  }
}

/** `notification` -- short feedback that dismisses itself. Mirrors `Viewport.notify`. Mounts nothing
  * at the call site; see the file-level doc comment for why.
  */
private[bridge] object NotificationFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent = {
    val message = ControlFactories.str(options("message"))
    val kind = options
      .get("kind")
      .map(ControlFactories.str)
      .map(ViewportFactories.notificationKind)
      .getOrElse(Viewport.NotificationKind.Info)
    val durationMs = options.get("durationMs").map(ControlFactories.int).getOrElse(3000)

    val conf = Viewport.notify(message, kind, durationMs)(using parent)
    parent.addDisposable(Disposable(Viewport.closeNotification(conf)))
    parent
  }
}
