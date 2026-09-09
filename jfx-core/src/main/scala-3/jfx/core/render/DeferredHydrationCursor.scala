package jfx.core.render

import jfx.core.async.AsyncRenderContext
import jfx.core.state.Disposable

/** Inserts normally but keeps activation callbacks behind an enclosing hydration session. */
private[render] final class DeferredHydrationCursor(delegate: Cursor, defer: (() => Unit) => Unit)
    extends Cursor {
  override def supportsAnchors = delegate.supportsAnchors
  override def isBrowser = delegate.isBrowser
  override def browserUrl = delegate.browserUrl
  override def asyncContext: Option[AsyncRenderContext] = delegate.asyncContext
  override def parentHost = delegate.parentHost
  override def afterHydration(callback: () => Unit): Unit = defer(callback)
  def claimElement(tag: String): HostElement = delegate.claimElement(tag)
  def claimText(initial: String): TextNode = delegate.claimText(initial)
  override def claimComment(text: String): CommentNode = delegate.claimComment(text)
  def sub(host: HostElement): Cursor = new DeferredHydrationCursor(delegate.sub(host), defer)
  override def before(node: HostNode): Cursor = new DeferredHydrationCursor(delegate.before(node), defer)
  override def fresh: Cursor = new DeferredHydrationCursor(delegate.fresh, defer)
  override def withHydrationBoundary(body: Cursor => Unit): Disposable = {
    var active = true
    val scoped = new DeferredHydrationCursor(delegate, callback => defer(() => if (active) callback()))
    try {
      body(scoped)
      Disposable { active = false }
    } catch {
      case error: Throwable =>
        active = false
        throw error
    }
  }
}
