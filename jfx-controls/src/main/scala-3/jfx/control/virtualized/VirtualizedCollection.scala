package jfx.control.virtualized

import jfx.core.component.AbstractComponent
import jfx.core.layout.Div
import jfx.core.remote.RemoteListDataSource
import jfx.core.render.DomHostElement
import jfx.core.state.{CompositeDisposable, Disposable, ListDataSource, ListProperty, Property}
import org.scalajs.dom

import scala.concurrent.Future

/** Shared base for TableView, DataGrid, and VirtualListView.
  *
  * Before P3-1, the three shared about 70 to 100 identically named members -- scroll state,
  * viewport measurement, remote integration, item state, revision counters, and DOM access were
  * implemented three times. Every fix had to be made three times; `requestLazyLoadIfNecessary` was
  * not (see jfx-controls/VIRTUALIZATION.md).
  *
  * What actually distinguishes the three lives in [[ItemGeometry]].
  *
  * The subclass provides:
  *   - [[geometry]] -- where item i is located and what is visible
  *   - [[renderableCount]] -- how many items can be rendered in total
  *   - [[recomputeVisible]] -- rebuilding the control-specific visible list
  *   - [[handleLocalItemsChange]] and [[resetMeasurements]]
  */
abstract class VirtualizedCollection[T](protected val dataSource: ListDataSource[T])
    extends AbstractComponent {

  // --- supplied by the subclass -------------------------------------------

  protected def geometry: ItemGeometry

  /** Number of renderable items. For a remote list, this is the total number rather than the number
    * already loaded.
    */
  protected def renderableCount: Int

  /** Rebuilds the control-specific list of visible items. */
  protected def recomputeVisible(): Unit

  /** Responds to a change in a local (non-remote) list. */
  protected def handleLocalItemsChange(change: ListProperty.Change[T]): Unit

  /** Discards cached measurements; default: nothing to do. */
  protected def resetMeasurements(): Unit = ()

  /** Can the list still grow? Controls loading more at the end. */
  protected def canStillGrow: Boolean =
    currentRemoteItems match {
      case null   => false
      case remote => remote.canLoadMore
    }

  // --- state ---------------------------------------------------------------

  val scrollTopProperty: Property[Double]      = Property(0.0)
  val viewportHeightProperty: Property[Double] = Property(400.0)
  val prefetchItemsProperty: Property[Int]     = Property(80)

  protected val itemStateRevisionProperty: Property[Int]   = Property(0)
  protected val remoteStateRevisionProperty: Property[Int] = Property(0)

  protected var itemsObserver: Disposable       = Disposable.empty
  protected var remoteItemsObserver: Disposable = Disposable.empty
  protected var viewportComponent: Div | Null   = null

  protected var viewportMeasureScheduled = false
  protected var browserRendering         = false
  protected var hydrating                = false

  /** The item list as a remote list, or null.
    */
  protected def currentRemoteItems: RemoteListDataSource[T] | Null =
    dataSource match {
      case remote: RemoteListDataSource[?] => remote.asInstanceOf[RemoteListDataSource[T]]
      case _                               => null
    }

  protected def itemAt(index: Int): Option[T] = dataSource.itemAt(index)

  protected def remoteLoading: Boolean =
    currentRemoteItems match {
      case null   => false
      case remote => remote.loadingProperty.get
    }

  protected def remoteError: Option[Throwable] =
    currentRemoteItems match {
      case null   => None
      case remote => remote.errorProperty.get
    }

  // --- revision counters ---------------------------------------------------

  protected def bumpItemState(): Unit =
    itemStateRevisionProperty.setAlways(itemStateRevisionProperty.get + 1)

  protected def bumpRemoteState(): Unit =
    remoteStateRevisionProperty.setAlways(remoteStateRevisionProperty.get + 1)

  protected def refreshItemState(): Unit = {
    bumpItemState()
    recomputeVisible()
  }

  // --- observers -----------------------------------------------------------

  /** Attaches observers to the data source supplied at construction. [[onRemoteSortingChanged]] is
    * the hook for CrawlableCollection.
    */
  protected def wireItemsObserver(): Unit = {
    itemsObserver.dispose()
    remoteItemsObserver.dispose()
    bumpRemoteState()

    val remote = currentRemoteItems

    itemsObserver = dataSource.observeChanges { change =>
      if (remote == null) handleLocalItemsChange(change)
      else {
        change match {
          case ListDataSource.Reset(_) => resetMeasurements()
          case _                       => ()
        }
        bumpRemoteState()
        refreshItemState()
      }
    }

    currentRemoteItems match {
      case null   => remoteItemsObserver = Disposable.empty
      case remote =>
        val composite = new CompositeDisposable()

        composite.add(remote.loadingProperty.observe { _ =>
          bumpRemoteState()
          refreshItemState()
        })
        composite.add(remote.errorProperty.observe { _ =>
          bumpRemoteState()
          refreshItemState()
        })
        composite.add(remote.totalCountProperty.observe(_ => refreshItemState()))
        composite.add(remote.hasMoreProperty.observe(_ => refreshItemState()))
        composite.add(remote.sortingProperty.observeWithoutInitial { sorting =>
          resetMeasurements()
          refreshItemState()
          onRemoteSortingChanged(sorting)
        })

        remoteItemsObserver = composite

        if (
          browserRendering && remote.loadedLength == 0 &&
          !remote.loadingProperty.get && remote.errorProperty.get.isEmpty
        ) discardResult(remote.reload())
    }

    resetMeasurements()
    refreshItemState()
  }

  /** Hook for CrawlableCollection; nothing to do without crawl state. */
  protected def onRemoteSortingChanged(
      sorting: Vector[jfx.core.remote.RemoteSort]
  ): Unit = ()

  // --- scrolling and measurement ------------------------------------------

  protected def updateScrollState(element: dom.html.Element): Unit = {
    scrollTopProperty.set(element.scrollTop)
    onScrollLeftChanged(element.scrollLeft)
    updateViewportSize(element)
  }

  /** Hook for horizontally scrolling controls; only TableView needs it. */
  protected def onScrollLeftChanged(scrollLeft: Double): Unit = ()

  protected def updateViewportSize(element: dom.html.Element): Unit =
    applyViewportSize(element.clientWidth.toDouble, element.clientHeight.toDouble)

  /** Applies a measured viewport size.
    *
    * Separated from [[updateViewportSize]] so applying it is testable without a DOM. The protected
    * bug was here: the base initially applied only height because it came from VirtualListView.
    * TableView and DataGrid also need width; without it both remained at their initial 800, the
    * grid showed one column too few, and the table distributed widths over too narrow a surface.
    */
  private[control] def applyViewportSize(width: Double, height: Double): Unit = {
    if (width > 0) onViewportWidthMeasured(width)
    if (height > 0) viewportHeightProperty.set(height)
  }

  /** Hook for controls with horizontal extent. VirtualListView is single-column and needs no width.
    */
  protected def onViewportWidthMeasured(width: Double): Unit = ()

  protected def scheduleViewportMeasure(): Unit =
    if (!viewportMeasureScheduled && browserRendering) {
      viewportMeasureScheduled = true
      dom.window.requestAnimationFrame { _ =>
        viewportMeasureScheduled = false
        domElement(viewportComponent).foreach { viewport =>
          measureViewport(viewport.clientWidth.toDouble, viewport.clientHeight.toDouble)
        }
      }
      ()
    }

  /** Body of a viewport measurement: apply size, then notify follow-ups.
    *
    * Separated from requestAnimationFrame so the chain is testable without a DOM. A bug lived here:
    * consolidation in P3-1 lost the second step, so saved scroll position was never restored and
    * hydrating remained true permanently, which in turn blocked loading more.
    */
  private[control] def measureViewport(width: Double, height: Double): Unit = {
    applyViewportSize(width, height)
    onViewportMeasured()
  }

  /** Hook after a viewport measurement. CrawlableCollection uses it to restore saved scroll
    * position and release hydration.
    */
  protected def onViewportMeasured(): Unit = ()

  /** Attaches item observers and ensures they are removed when the component is disposed. Called by
    * the subclass's installObservers.
    */
  protected def installItemObservers(): Unit = {
    addDisposable(Disposable {
      itemsObserver.dispose()
      remoteItemsObserver.dispose()
    })
    wireItemsObserver()
  }

  /** Observes the viewport size.
    *
    * This appeared identically in afterCompose of all three controls: a ResizeObserver on the
    * viewport plus a window resize listener, both targeting scheduleViewportMeasure.
    */
  protected def observeViewportSize(): Unit = {
    domElement(viewportComponent).foreach { element =>
      val observer = new dom.ResizeObserver((_, _) => scheduleViewportMeasure())
      observer.observe(element)
      addDisposable(Disposable(observer.disconnect()))
    }

    val listener: dom.Event => Unit = _ => scheduleViewportMeasure()
    dom.window.addEventListener("resize", listener)
    addDisposable(Disposable(dom.window.removeEventListener("resize", listener)))
  }

  /** Continuously measures a header element's height and writes it to `target`. The three controls
    * differ only in which element and property they provide.
    */
  protected def observeHeaderHeight(
      header: AbstractComponent | Null,
      target: Property[Double]
  ): Unit =
    domElement(header).foreach { element =>
      val measure = () => {
        val value = math.max(0.0, element.offsetHeight.toDouble)
        if (math.abs(target.get - value) > 0.5) target.set(value)
      }
      val frame    = dom.window.requestAnimationFrame(_ => measure())
      val observer = new dom.ResizeObserver((_, _) => measure())
      observer.observe(element)
      addDisposable(Disposable {
        dom.window.cancelAnimationFrame(frame)
        observer.disconnect()
      })
    }

  protected def domElement(component: AbstractComponent | Null): Option[dom.html.Element] =
    Option(component).flatMap { current =>
      current.host match {
        case domHost: DomHostElement =>
          domHost.node match {
            case element: dom.html.Element => Some(element)
            case _                         => None
          }
        case _ => None
      }
    }

  protected def topForIndex(index: Int): Double =
    geometry.topForIndex(index)

  /** Visible range as `[start, end)`.
    *
    * The first branch is identical in all three controls: during server rendering and hydration,
    * crawl state determines the slice rather than scroll position, otherwise the rendered slice
    * would not be reproducible. Only the else branch differs and belongs to geometry.
    */
  protected def visibleRange(total: Int): (Int, Int) =
    if ((!browserRendering || hydrating) && crawlWindow.nonEmpty) {
      val (offset, limit) = crawlWindow.get
      val start           = math.min(offset, total)
      (start, math.min(total, start + limit))
    } else {
      geometry.visibleRange(total, scrollTopProperty.get, viewportHeightProperty.get)
    }

  /** Slice to show during server rendering, or None. CrawlableCollection overrides this.
    */
  protected def crawlWindow: Option[(Int, Int)] = None

  // --- loading more --------------------------------------------------------

  /** Runs prefetching for the visible range.
    *
    * This version comes from DataGrid and VirtualListView; TableView had an older one without a
    * prefetch window or page alignment and inherits the improvement here. See VIRTUALIZATION.md.
    */
  protected def requestLazyLoadIfNecessary(start: Int, end: Int): Unit =
    currentRemoteItems match {
      case null                                        => ()
      case remote if remote.errorProperty.get.nonEmpty => ()
      case remote if remote.supportsRangeLoading       =>
        val prefetch    = math.max(1, prefetchItemsProperty.get)
        val total       = renderableCount
        val requestFrom = math.max(0, start - prefetch)
        val requestTo   = math.min(total, end + prefetch)
        val pageSize    = math.max(prefetch, math.max(1, end - start))
        val pageFrom    = requestFrom / pageSize * pageSize
        val pageTo      = math.min(
          total,
          math.max(pageFrom + 1, ((requestTo + pageSize - 1) / pageSize) * pageSize)
        )
        // Deduplication belongs to RemoteListProperty: identical requests share a Future there. The
        // control need not keep bookkeeping for them.
        if (pageTo > pageFrom && !remote.isRangeLoaded(pageFrom, pageTo)) {
          discardResult(remote.ensureRangeLoaded(pageFrom, pageTo))
        }
      case remote if canStillGrow =>
        val threshold = math.max(1, prefetchItemsProperty.get / 2)
        if (remote.loadedLength == 0) discardResult(remote.reload())
        else if (end >= math.max(0, remote.loadedLength - threshold))
          discardResult(remote.loadMore())
      case _ => ()
    }

  /** A loading Future whose result the control does not need. recover prevents an unhandled
    * failure; the error itself lives in RemoteListProperty.errorProperty and is rendered from
    * there.
    */
  protected def discardResult(result: Future[?]): Unit = {
    result.recover { case _ => () }(using scala.concurrent.ExecutionContext.global)
    ()
  }
}
