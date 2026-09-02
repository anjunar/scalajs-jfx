package jfx.control.virtualized

import jfx.control.CrawlCookieState
import jfx.core.context.CrawlScope
import jfx.core.remote.{RemoteListDataSource, RemoteSort}
import jfx.core.state.{Disposable, Property}
import org.scalajs.dom

/** Crawlbarkeit einer virtualisierten Collection.
  *
  * Ein Crawler sieht kein Scrollen. Damit trotzdem mehr als der erste Ausschnitt indexierbar ist,
  * rendert das Control beim Server-Rendering einen festen Ausschnitt und dazu einen Link auf den
  * naechsten -- der Ausschnitt kommt aus einem Cookie pro Control-ID, der Link aus dem
  * [[CrawlScope]] (P1-4).
  *
  * Vor P3-1 lag dieser Block dreimal parallel in TableView, DataGrid und VirtualListView, in drei
  * nicht mehr deckungsgleichen Fassungen.
  *
  * '''Cookie und nicht Query-Parameter''' -- entschieden in P3-2. Der Zustand bleibt im Cookie. Er
  * beschreibt, wo ein Besucher in einer Liste stand, nicht was die Seite zeigt; in die URL gehoerte
  * er nur, wenn er teilbar sein soll, und das ist er ausdruecklich nicht. Wer den Vorschlag aus
  * CHANGE.md wieder aufgreifen will (`?table.offset=…`), muss diese Entscheidung kippen, nicht
  * bloss die Umsetzung nachholen.
  */
trait CrawlableCollection[T] { self: VirtualizedCollection[T] =>

  /** Name des Controls fuer die Fehlermeldung, wenn die Crawl-ID fehlt. */
  protected def crawlControlName: String

  /** Wie viele Elemente ein Crawl-Ausschnitt standardmaessig umfasst. */
  protected def crawlDefaultLimit: Int

  val crawlableProperty: Property[Boolean]      = Property(false)
  val crawlIdProperty: Property[Option[String]] = Property(None)

  protected var crawlState: CrawlCookieState.State =
    CrawlCookieState.State(0, crawlDefaultLimit, None)

  protected var resolvedCrawlId: Option[String] = None

  /** Beim Server-Rendering angesprungener Index; -1, wenn keiner. */
  protected var initialScrollIndex: Int = -1

  protected def crawlParams: (Int, Int) = crawlState.offset -> crawlState.limit

  override protected def crawlWindow: Option[(Int, Int)] =
    Option.when(crawlableProperty.get)(crawlParams)

  protected def initializeCrawlState(): Unit =
    if (crawlableProperty.get) {
      val id = CrawlCookieState.requireId(crawlIdProperty.get, crawlControlName)
      resolvedCrawlId = Some(id)
      crawlState = CrawlCookieState.resolve(
        id,
        crawlDefaultLimit,
        browserRendering
      )(using this)
    } else {
      resolvedCrawlId = None
      crawlState = CrawlCookieState.State(0, crawlDefaultLimit, None)
    }

  protected def refreshConfiguredCrawlState(): Unit = {
    initializeCrawlState()
    resolvedCrawlId match {
      case Some(id) => setAttribute("id", id)
      case None     => removeAttribute("id")
    }
    if (browserRendering) initializeBrowserCrawlState()
    refreshItemState()
  }

  protected def initializeBrowserCrawlState(): Unit =
    resolvedCrawlId.foreach { _ =>
      val initialSorting = crawlState.sorting
      val activeSorting  =
        Option(currentRemoteItems).fold(Vector.empty[RemoteSort])(_.getSorting)

      crawlState = crawlState.withSorting(initialSorting.getOrElse(activeSorting))
      persistCrawlState(crawlState)

      for {
        sorting <- initialSorting
        remote  <- Option(currentRemoteItems)
        if remote.supportsSorting && sorting != activeSorting
      } scheduleSortingRestore(remote, sorting)
    }

  protected def persistCrawlState(state: CrawlCookieState.State): Unit =
    resolvedCrawlId.foreach(id => CrawlCookieState.write(id, state, browserRendering))

  /** Der Sortier-Zustand aus dem Cookie wird erst im naechsten Frame angewandt. Direkt waehrend der
    * Komposition wuerde das Neuladen die gerade aufgebaute Sichtliste unter den Fuessen wegziehen.
    */
  protected def scheduleSortingRestore(
      remote: RemoteListDataSource[T],
      sorting: Vector[RemoteSort]
  ): Unit = {
    var active = true
    val frame  = dom.window.requestAnimationFrame { _ =>
      if (active) discardResult(remote.applySorting(sorting))
    }
    addDisposable(Disposable {
      active = false
      dom.window.cancelAnimationFrame(frame)
    })
  }

  override protected def onRemoteSortingChanged(sorting: Vector[RemoteSort]): Unit =
    if (browserRendering && resolvedCrawlId.nonEmpty) {
      crawlState = crawlState.withSorting(sorting)
      persistCrawlState(crawlState)
    }

  protected def hasMoreCrawlPage: Boolean = {
    val (offset, limit) = crawlParams
    crawlableProperty.get && offset + limit < renderableCount
  }

  /** Ziel des Crawl-Links: der aktuelle Pfad aus dem CrawlScope.
    *
    * Frueher stand hier Router.current(...) -- dafuer hing die gesamte Control-Bibliothek am
    * Router. Siehe CHANGE.md P1-4.
    */
  protected def nextCrawlHref: String =
    CrawlScope.path(using this)

  /** Springt die aus dem Cookie wiederhergestellte Position an und gibt die Hydration frei.
    *
    * Die Freigabe von hydrating ist nicht nebensaechlich: solange sie aussteht, rendert das Control
    * weiter den Crawl-Ausschnitt und laedt nichts nach.
    */
  override protected def onViewportMeasured(): Unit =
    if (initialScrollIndex > 0) {
      val nextScrollTop = topForIndex(initialScrollIndex)
      hydrating = false
      domElement(viewportComponent).foreach(_.scrollTop = nextScrollTop)
      scrollTopProperty.set(nextScrollTop)
      initialScrollIndex = -1
      recomputeVisible()
    } else if (hydrating) {
      hydrating = false
      recomputeVisible()
    }

  protected def persistVisibleScrollOffset(): Unit =
    if (browserRendering && !hydrating && resolvedCrawlId.nonEmpty) {
      val total  = renderableCount
      val offset =
        if (total <= 0) 0
        else
          math.min(
            total - 1,
            geometry.indexForOffset(math.max(0.0, scrollTopProperty.get - geometry.headerOffset))
          )

      if (offset != crawlState.offset) {
        crawlState = crawlState.copy(offset = offset)
        persistCrawlState(crawlState)
      }
    }
}
