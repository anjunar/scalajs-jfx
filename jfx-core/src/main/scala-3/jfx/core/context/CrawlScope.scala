package jfx.core.context

import jfx.core.component.AbstractComponent
import jfx.core.di.Context

/** The path at which the currently displayed page is reachable.
  *
  * Virtualizing controls (TableView, DataGrid, VirtualListView) render a link to the next data page
  * for crawlers. They need exactly one piece of information: the current page's path. They do not
  * need routing for this -- a generic table must not know that a router exists.
  *
  * The application decides who provides the scope. Usually it is the router (jfx-router provides
  * itself as a CrawlScope in `compose`); an application without a router may use any other source.
  *
  * When the scope is missing, [[CrawlScope.path]] returns an empty string -- the controls then
  * render no crawl link. This is the correct failure mode: without a known path, there is no link a
  * crawler can meaningfully follow.
  */
trait CrawlScope {

  /** Current page path without the base path, e.g. "/table". */
  def path: String
}

object CrawlScope {

  val CrawlScopeContext: Context[CrawlScope] =
    Context.create[CrawlScope]("CrawlScope")

  def current(using component: AbstractComponent): Option[CrawlScope] =
    CrawlScopeContext.inject

  def provide(scope: CrawlScope)(using component: AbstractComponent): Unit =
    CrawlScopeContext.provide(scope)

  /** Current page path, or "" when no scope is available. */
  def path(using component: AbstractComponent): String =
    current.map(_.path).filter(_.nonEmpty).getOrElse("")

  /** CrawlScope backed by a function that reads the path afresh on every call. */
  def apply(currentPath: () => String): CrawlScope =
    new CrawlScope {
      override def path: String = currentPath()
    }
}
