package jfx.control.virtuallist

import jfx.control.virtualized.{CrawlableCollection, MeasuredRowGeometry, VirtualizedCollection}
import jfx.core.component.AbstractComponent
import jfx.core.remote.RemoteSort
import jfx.core.dsl.ClassDsl.{addClass, classIf, classes}
import jfx.core.dsl.DslLayer
import jfx.core.dsl.EventDsl.{on, onClick}
import jfx.core.dsl.StyleDsl.*
import jfx.core.layout.Anchor.{anchor, href}
import jfx.core.layout.Condition.when
import jfx.core.layout.Div
import jfx.core.layout.Div.div
import jfx.core.render.{Cursor, DomHostElement}
import jfx.core.state.*
import jfx.core.statement.Foreach.foreach
import jfx.core.context.CrawlScope
import org.scalajs.dom

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

final class VirtualListView[T] private (
    source: ListDataSource[T],
    configure: VirtualListView[T] ?=> Cursor ?=> Unit
) extends VirtualizedCollection[T](source),
      CrawlableCollection[T] {

  private given ExecutionContext = ExecutionContext.global

  override val tagName: String = "div"

  val estimateHeightProperty: Property[Double] = Property(44.0)
  val overscanPxProperty: Property[Double]     = Property(240.0)

  private val visibleSlotsProperty = ListProperty[VirtualListView.VisibleSlot[T]]()
  private val headerHeightProperty = Property(0.0)

  /** Gemessene Hoehen, eine Spalte. Alles, was VirtualListView von TableView und DataGrid
    * unterscheidet, steckt hier -- der Rest kommt aus VirtualizedCollection und
    * CrawlableCollection.
    */
  override protected val geometry: MeasuredRowGeometry =
    new MeasuredRowGeometry(
      estimateHeight = () => estimateHeight,
      headerHeightValue = () => headerHeight,
      overscanPx = () => overscanPxProperty.get
    )

  override protected def crawlControlName: String = "VirtualListView"
  override protected def crawlDefaultLimit: Int   = VirtualListView.defaultLimit

  private var lastVisibleSlots = Vector.empty[VirtualListView.VisibleSlot[T]]
  private var tailPaddingItems = defaultTailPadding
  private var cellRendererBody: Option[VirtualListView.Renderer[T]]     = None
  private var headerBody: Option[AbstractComponent ?=> Cursor ?=> Unit] = None
  private var headerComponent: Div | Null                               = null
  private var compositionReady                                          = false

  def items: ListDataSource[T] = dataSource

  def setRenderer(renderer: VirtualListView.Renderer[T]): Unit = {
    cellRendererBody = Option(renderer)
    if (compositionReady) {
      lastVisibleSlots = Vector.empty
      refreshItemState()
    }
  }

  def getRenderer: VirtualListView.Renderer[T] =
    cellRendererBody.getOrElse(VirtualListView.emptyRenderer[T])

  private[control] def setHeader(
      body: AbstractComponent ?=> Cursor ?=> Unit
  ): Unit =
    headerBody = Some(body)

  def refresh(): Unit = refreshItemState()

  def scrollTo(index: Int): Unit = {
    val total = renderableCount
    if (total > 0) {
      val clamped       = math.max(0, math.min(total - 1, index))
      val nextScrollTop = geometry.topForIndex(clamped)
      scrollTopProperty.set(nextScrollTop)
      domElement(viewportComponent).foreach(_.scrollTop = nextScrollTop)
    }
  }

  override def compose(cursor: Cursor): Unit = {
    browserRendering = cursor.isBrowser
    hydrating = cursor.isHydrating

    // Structural configuration must be complete before Foreach claims its
    // SSR or hydration range.
    configure(using this)(using cursor)
    initializeCrawlState()
    if (browserRendering && crawlState.offset > 0) {
      initialScrollIndex = crawlState.offset
      if (!hydrating) scrollTopProperty.set(topForIndex(crawlState.offset))
    }
    installObservers()

    DslLayer.render(this, cursor) {
      addClass("jfx-virtual-list")
      resolvedCrawlId.foreach(setAttribute("id", _))
      classIf("jfx-virtual-list-loading", remoteStateRevisionProperty.map(_ => remoteLoading))
      classIf("jfx-virtual-list-error", remoteStateRevisionProperty.map(_ => remoteError.nonEmpty))
      style {
        display = "block"
        width = "100%"
        height = "100%"
        minWidth = "0"
        minHeight = "0"
        overflow = "hidden"
        position = "relative"
      }

      viewportComponent = div {
        classes = Seq("jfx-virtual-list-viewport")
        style {
          width = "100%"
          height = "100%"
          overflow = "auto"
          position = "relative"
        }

        on("scroll") { event =>
          event.raw match {
            case raw: dom.Event =>
              raw.currentTarget match {
                case element: dom.html.Element => updateScrollState(element)
                case _                         => ()
              }
            case _ => ()
          }
        }

        div {
          classes = Seq("jfx-virtual-list-content")
          style {
            width = "100%"
            minHeight = "100%"
          }

          headerComponent = div {
            classes = Seq("jfx-virtual-list-header-slot")
            style {
              width = "100%"
              boxSizing = "border-box"
            }
            headerBody.foreach { body => body }
          }

          div {
            classes = Seq("jfx-virtual-list-items-surface")
            style {
              position = "relative"
              width = "100%"
              minHeight = "100%"
              if (browserRendering) {
                height = itemStateRevisionProperty.map(_ => s"${contentHeight}px")
              }
            }

            foreach(visibleSlotsProperty) { slot =>
              VirtualListCell.virtualListCell[T](slot, getRenderer, handleMeasuredHeight) {}
            }
          }

          when(itemStateRevisionProperty.map(_ => hasMoreCrawlPage)) {
            val (offset, limit) = crawlParams
            anchor("More items...") {
              classes = Seq("jfx-virtual-list-more-link")
              href = nextCrawlHref
              onClick(_ => persistCrawlState(crawlState.copy(offset = offset + limit)))
              style {
                display = "block"
                padding = "20px"
                textAlign = "center"
                marginTop =
                  if (browserRendering) "0px" else s"${geometry.offsetFor(offset + limit)}px"
              }
            }
          }
        }
      }
    }

    compositionReady = true
  }

  override def afterCompose(cursor: Cursor): Unit =
    if (browserRendering) {
      initializeBrowserCrawlState()
      scheduleViewportMeasure()
      observeHeaderHeight(headerComponent, headerHeightProperty)
      observeViewportSize()
    }

  private def installObservers(): Unit = {
    addDisposable(scrollTopProperty.observeWithoutInitial { _ =>
      recomputeVisible()
      persistVisibleScrollOffset()
    })
    addDisposable(viewportHeightProperty.observeWithoutInitial(_ => recomputeVisible()))
    addDisposable(overscanPxProperty.observeWithoutInitial(_ => recomputeVisible()))
    addDisposable(prefetchItemsProperty.observeWithoutInitial(_ => recomputeVisible()))
    addDisposable(crawlableProperty.observeWithoutInitial(_ => refreshConfiguredCrawlState()))
    addDisposable(crawlIdProperty.observeWithoutInitial(_ => refreshConfiguredCrawlState()))
    addDisposable(estimateHeightProperty.observeWithoutInitial { _ =>
      resetMeasurements()
      refreshItemState()
    })
    addDisposable(headerHeightProperty.observeWithoutInitial(_ => refreshItemState()))
    installItemObservers()
  }

  override protected def recomputeVisible(): Unit = {
    geometry.rebuildPrefixIfDirty()
    val total = renderableCount

    if (total <= 0) publishVisibleSlots(Seq.empty)
    else {
      val (start, end) = visibleRange(total)
      publishVisibleSlots((start until end).map(slotFor))
      if (browserRendering && !hydrating && end > start) requestLazyLoadIfNecessary(start, end)
    }
  }

  override protected def handleLocalItemsChange(change: ListProperty.Change[T]): Unit = {
    change match {
      case ListProperty.UpdateAt(index, _, _, _) => invalidateVisibleSlot(index)
      case ListProperty.Add(_, _)                => ()
      case _                                     => resetMeasurements()
    }
    refreshItemState()
  }

  override protected def resetMeasurements(): Unit = {
    geometry.clear()
    tailPaddingItems = defaultTailPadding
    lastVisibleSlots = Vector.empty
  }

  private def slotFor(index: Int): VirtualListView.VisibleSlot[T] =
    VirtualListView.VisibleSlot(
      index = index,
      item = itemAt(index),
      top = geometry.offsetFor(index),
      height = geometry.heightFor(index)
    )

  private def publishVisibleSlots(slots: Seq[VirtualListView.VisibleSlot[T]]): Unit = {
    val next = slots.toVector
    if (next != lastVisibleSlots) {
      lastVisibleSlots = next
      visibleSlotsProperty.setAll(next)
    }
  }

  private def invalidateVisibleSlot(index: Int): Unit =
    if (lastVisibleSlots.exists(_.index == index)) lastVisibleSlots = Vector.empty

  private[virtuallist] def handleMeasuredHeight(index: Int, height: Double): Unit =
    if (height > 0) {
      val anchorIndex =
        geometry.indexForOffset(math.max(0.0, scrollTopProperty.get - headerHeight))
      geometry.updateHeight(index, height).foreach { delta =>
        geometry.rebuildPrefixIfDirty()
        bumpItemState()
        if (browserRendering && index < anchorIndex && math.abs(delta) > 0.5) {
          val adjusted = math.max(0.0, scrollTopProperty.get + delta)
          scrollTopProperty.set(adjusted)
          domElement(viewportComponent).foreach(_.scrollTop = adjusted)
        } else recomputeVisible()
      }
    }

  private def knownItemCount: Option[Int] =
    currentRemoteItems match {
      case null   => Some(dataSource.totalLength)
      case remote => remote.totalCountProperty.get
    }

  /** Weiter gefasst als der Standard der Basis: solange noch geladen wird oder die Gesamtzahl
    * unbekannt ist, reserviert die Liste Platz am Ende, damit das Scrollen nicht am vorlaeufigen
    * Ende haengenbleibt.
    */
  override protected def canStillGrow: Boolean =
    Option(currentRemoteItems).exists { remote =>
      remote.loadingProperty.get || remote.canLoadMore || remote.totalCountProperty.get.isEmpty
    }

  override protected def renderableCount: Int =
    knownItemCount.getOrElse {
      val loaded = Option(currentRemoteItems).fold(dataSource.totalLength)(_.loadedLength)
      if (canStillGrow) loaded + tailPaddingItems else loaded
    }

  private def contentHeight: Double =
    geometry.contentHeight(renderableCount)

  private def estimateHeight: Double  = math.max(1.0, estimateHeightProperty.get)
  private def headerHeight: Double    = math.max(0.0, headerHeightProperty.get)
  private def defaultTailPadding: Int = math.max(1, prefetchItemsProperty.get) * 3

}

object VirtualListView {
  type Renderer[T] = (T | Null, Int) => AbstractComponent ?=> Cursor ?=> Unit

  private val defaultLimit = 50

  private[control] final case class VisibleSlot[T](
      index: Int,
      item: Option[T],
      top: Double,
      height: Double
  )

  private def emptyRenderer[T]: Renderer[T] =
    (_: T | Null, _: Int) => (_: AbstractComponent) ?=> (_: Cursor) ?=> ()

  def virtualList[T](
      source: ListDataSource[T]
  )(
      body: VirtualListView[T] ?=> Cursor ?=> Unit
  )(using AbstractComponent, Cursor): VirtualListView[T] =
    DslLayer.child(new VirtualListView[T](source, body)) {}

  def items[T](using list: VirtualListView[T]): ListDataSource[T] = list.items

  def estimateHeight(using list: VirtualListView[?]): Double = list.estimateHeightProperty.get
  def estimateHeight_=(value: Double)(using list: VirtualListView[?]): Unit =
    list.estimateHeightProperty.set(math.max(1.0, value))

  def estimateHeightPx(using list: VirtualListView[?]): Double = list.estimateHeightProperty.get
  def estimateHeightPx_=(value: Double)(using list: VirtualListView[?]): Unit =
    list.estimateHeightProperty.set(math.max(1.0, value))

  def overscanPx(using list: VirtualListView[?]): Double = list.overscanPxProperty.get
  def overscanPx_=(value: Double)(using list: VirtualListView[?]): Unit =
    list.overscanPxProperty.set(math.max(0.0, value))

  def prefetchItems(using list: VirtualListView[?]): Int = list.prefetchItemsProperty.get
  def prefetchItems_=(value: Int)(using list: VirtualListView[?]): Unit =
    list.prefetchItemsProperty.set(math.max(1, value))

  def crawlable(using list: VirtualListView[?]): Boolean                = list.crawlableProperty.get
  def crawlable_=(value: Boolean)(using list: VirtualListView[?]): Unit =
    list.crawlableProperty.set(value)

  def crawlId(using list: VirtualListView[?]): Option[String]        = list.crawlIdProperty.get
  def crawlId_=(value: String)(using list: VirtualListView[?]): Unit =
    list.crawlIdProperty.set(Option(value))

  def cellRenderer[T](using list: VirtualListView[T]): Renderer[T]                = list.getRenderer
  def cellRenderer_=[T](value: Renderer[T])(using list: VirtualListView[T]): Unit =
    list.setRenderer(value)

  def header[T](body: AbstractComponent ?=> Cursor ?=> Unit)(using list: VirtualListView[T]): Unit =
    list.setHeader(body)
}
