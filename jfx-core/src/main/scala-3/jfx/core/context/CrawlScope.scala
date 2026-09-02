package jfx.core.context

import jfx.core.component.AbstractComponent
import jfx.core.di.Context

/**
 * Der Pfad, unter dem die aktuell dargestellte Seite erreichbar ist.
 *
 * Virtualisierende Controls (TableView, DataGrid, VirtualListView) rendern fuer
 * Crawler einen Link auf die naechste Datenseite. Dafuer brauchen sie genau eine
 * Information: den Pfad der aktuellen Seite. Sie brauchen dafuer kein Routing --
 * eine generische Tabelle darf nicht wissen, dass es einen Router gibt.
 *
 * Wer den Scope bereitstellt, entscheidet die Anwendung. Im Normalfall ist das
 * der Router (jfx-router stellt sich in seiner `compose` selbst als CrawlScope
 * bereit); eine Anwendung ohne Router kann jede andere Quelle einsetzen.
 *
 * Fehlt der Scope, liefert [[CrawlScope.path]] den leeren String -- die Controls
 * rendern dann keinen Crawl-Link. Das ist der richtige Ausfallmodus: ohne
 * bekannten Pfad gibt es keinen Link, den ein Crawler sinnvoll folgen koennte.
 */
trait CrawlScope {

  /** Pfad der aktuellen Seite, ohne Basis-Pfad, z. B. "/table". */
  def path: String
}

object CrawlScope {

  val CrawlScopeContext: Context[CrawlScope] =
    Context.create[CrawlScope]("CrawlScope")

  def current(using component: AbstractComponent): Option[CrawlScope] =
    CrawlScopeContext.inject

  def provide(scope: CrawlScope)(using component: AbstractComponent): Unit =
    CrawlScopeContext.provide(scope)

  /** Pfad der aktuellen Seite, oder "" wenn kein Scope bereitsteht. */
  def path(using component: AbstractComponent): String =
    current.map(_.path).filter(_.nonEmpty).getOrElse("")

  /** CrawlScope aus einer Funktion, die den Pfad bei jedem Aufruf frisch liest. */
  def apply(currentPath: () => String): CrawlScope =
    new CrawlScope {
      override def path: String = currentPath()
    }
}
