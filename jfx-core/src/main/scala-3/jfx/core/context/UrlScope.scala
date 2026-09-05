package jfx.core.context

import jfx.core.component.AbstractComponent
import jfx.core.di.Context

/** The URL and navigation operation belonging to the currently rendered route.
  *
  * This is deliberately smaller than a router API. Controls can keep their state in the address bar
  * without depending on jfx-router, while an application without a router can provide its own
  * implementation.
  */
trait UrlScope {
  def url: String
  def navigate(url: String, replace: Boolean): Unit
}

object UrlScope {
  val UrlScopeContext: Context[UrlScope] =
    Context.create[UrlScope]("UrlScope")

  def current(using component: AbstractComponent): Option[UrlScope] =
    UrlScopeContext.inject

  def provide(scope: UrlScope)(using component: AbstractComponent): Unit =
    UrlScopeContext.provide(scope)

  def apply(currentUrl: () => String)(navigateTo: (String, Boolean) => Unit): UrlScope =
    new UrlScope {
      override def url: String                                   = currentUrl()
      override def navigate(url: String, replace: Boolean): Unit = navigateTo(url, replace)
    }
}
