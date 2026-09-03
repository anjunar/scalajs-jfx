package jfx.bridge

import jfx.core.async.AsyncRenderContext
import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.render.{Cursor, DomCursor, HydratingCursor}
import jfx.core.state.{ListProperty => CoreListProperty, Property => CoreProperty}
import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

/** Mirrors `contract.ts`'s `JfxRuntime`. The one instance of this class -- [[BridgeRuntime]]'s
  * `bridgeRuntime` -- is installation-wide and constant, exactly as the contract's own doc comment
  * says a `JfxRuntime` must be: everything request-scoped hangs off the `ScopeHandle` each method
  * below hands out, not off this object.
  */
final class JfxRuntimeBridge extends js.Object {

  val name: String = "jfx-bridge"

  def property[T](initial: T): PropertyHandle[T] =
    new PropertyHandle[T](CoreProperty(initial))

  def listProperty[T](initial: js.Array[T]): ListPropertyHandle[T] =
    new ListPropertyHandle[T](CoreListProperty[T](initial))

  /** Client-side render into an empty container. Synchronous, like `contract.ts` says: nothing here
    * awaits an `AsyncRenderContext`, so a `fetchInto` loader started during `build` resolves later,
    * against the already-mounted tree, the same way it would for a plain client-side `FetchComponent`
    * outside SSR.
    */
  def mount(root: dom.Element, build: js.Function1[ScopeHandleBridge, Unit]): MountedAppHandle = {
    val cursor = DomCursor.root(root)
    new MountedAppHandle(Runtime.mount(new BridgeRoot(build), cursor))
  }

  /** Claims a server-rendered tree. Mirrors `HydratingCursor`: `root` is `Document | Element`
    * because hydrating the whole document (`<html>` claims the document element, mirroring
    * `app.Main.boot`) and hydrating a single container (`mount`'s counterpart) claim through two
    * different `HydratingCursor.root` overloads.
    */
  def hydrate(
      root: js.Any,
      build: js.Function1[ScopeHandleBridge, Unit]
  ): js.Promise[MountedAppHandle] = {
    given ExecutionContext = ExecutionContext.global

    val async = new AsyncRenderContext()

    val cursor: HydratingCursor = root match {
      case document: dom.Document => HydratingCursor.root(document, async)
      case element: dom.Element   => HydratingCursor.root(element, async)
      case _                      =>
        throw new IllegalArgumentException("hydrate() expects a Document or an Element.")
    }

    var mountedRoot = Option.empty[AbstractComponent]

    val hydration =
      try {
        mountedRoot = Some(Runtime.mount(new BridgeRoot(build), cursor))
        async.drain().map { _ =>
          cursor.completeHydration()
          new MountedAppHandle(mountedRoot.get)
        }
      } catch {
        case error: Throwable => Future.failed(error)
      }

    hydration
      .recoverWith { case error =>
        async.cancel()
        mountedRoot.foreach(Runtime.unmount)
        Future.failed(error)
      }
      .toJSPromise
  }

  /** Server-side render. `status` and `headers` are fixed at `200` / empty: a route's status --
    * `AppDocument.ssrStatus` in the demo application -- is domain state this core-only prototype
    * does not have. A consumer that needs it mounts a component that carries its own status the same
    * way `AppDocument` does, and reads it back the way `app.Main.render` does, once the bridge grows
    * past step 2 of JAVASCRIPT_API.md §9.
    */
  def renderToString(
      build: js.Function1[ScopeHandleBridge, Unit],
      options: js.UndefOr[SsrOptionsFacade]
  ): js.Promise[SsrResultHandle] = {
    given ExecutionContext = ExecutionContext.global

    val timeoutMs =
      options.toOption
        .flatMap(_.timeoutMs.toOption)
        .map(_.toInt)
        .getOrElse(Runtime.DefaultSsrTimeoutMs)

    Runtime
      .renderToStringAsync(
        cursor => Runtime.mount(new BridgeRoot(build), cursor),
        timeoutMs
      )
      .map(html => new SsrResultHandle(html, 200, js.Dictionary()))
      .toJSPromise
  }
}
