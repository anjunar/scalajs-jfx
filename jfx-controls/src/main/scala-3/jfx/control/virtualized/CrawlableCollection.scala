package jfx.control.virtualized

import jfx.control.CrawlCookieState
import jfx.core.context.CrawlScope
import jfx.core.remote.{RemoteListDataSource, RemoteSort}
import jfx.core.state.{Disposable, Property}
import org.scalajs.dom

/** Crawlability of a virtualized collection.
  *
  * A crawler cannot scroll. To make more than the first slice indexable, the control renders a
  * fixed slice during server rendering with a link to the next one -- the slice comes from a cookie
  * per control ID, and the link from [[CrawlScope]] (P1-4).
  *
  * Before P3-1, this block existed three times in TableView, DataGrid, and VirtualListView in three
  * no-longer-equivalent versions.
  *
  * '''Cookie rather than query parameters''' -- decided in P3-2. The state remains in the cookie.
  * It describes where a visitor was in a list, not what the page displays; it belongs in the URL only
  * if it should be shareable, which it explicitly should not. Anyone reviving the CHANGE.md proposal
  * (`?table.offset=…`) must reverse this decision rather than merely restore its implementation.
  */
trait CrawlableCollection[T] { self: VirtualizedCollection[T] =>

  /** Control name for the error message when the crawl ID is missing. */
  protected def crawlControlName: String

  /** Number of elements a crawl slice contains by default. */
  protected def crawlDefaultLimit: Int

  val crawlableProperty: Property[Boolean]      = Property(false)
  val crawlIdProperty: Property[Option[String]] = Property(None)

  protected var crawlState: CrawlCookieState.State =
    CrawlCookieState.State(0, crawlDefaultLimit, None)

  protected var resolvedCrawlId: Option[String] = None

  /** Index targeted during server rendering; -1 when none. */
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

  /** Applies cookie sorting state only in the next frame. During composition, reloading would pull
    * away the visible list currently being built.
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

  /** Crawl-link target: the current path from CrawlScope.
    *
    * This previously used Router.current(...), making the entire controls library depend on the
    * router. See CHANGE.md P1-4.
    */
  protected def nextCrawlHref: String =
    CrawlScope.path(using this)

  /** Moves to the position restored from the cookie and releases hydration.
    *
    * Releasing hydrating is essential: while it remains pending, the control keeps rendering the
    * crawl slice and loads nothing more.
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
