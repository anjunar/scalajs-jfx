package jfx.control

import jfx.core.component.{ElementComponent, FormSubtreeRegistration, ManagedElementComponent, NodeComponent}
import jfx.core.state.ListProperty.*
import jfx.core.state.*
import jfx.dsl.{ComponentContext, DslRuntime, Scope}
import jfx.form.Formular
import org.scalajs.dom.{Event, HTMLDivElement, Node, window}

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.compiletime.uninitialized


class VirtualListView[T](
  initialItems: ListProperty[T] = ListProperty(),
  val estimateHeightPx: Int = 44,
  val overscanPx: Int = 240,
  val prefetchItems: Int = 80,
  private val renderScope: Scope,
  private val renderEnclosingForm: Option[Formular[?, ?]],
  private val renderer: (T | Null, Int) => NodeComponent[? <: Node] | Null
) extends ElementComponent[HTMLDivElement], FormSubtreeRegistration {

  private given ExecutionContext = ExecutionContext.global

  private val itemsRefProperty: Property[ListProperty[T]] =
    Property(normalizeItems(initialItems))

  private val viewport: HTMLDivElement = newElement("div")
  private val content: HTMLDivElement = newElement("div")

  private val slotPool = mutable.ArrayBuffer.empty[SlotHost]
  private val heights = mutable.ArrayBuffer.empty[Int]
  private val prefix = mutable.ArrayBuffer(0)

  private var prefixDirtyFrom: Int = Int.MaxValue
  private var tailPaddingItems: Int = math.max(prefetchItems * 3, prefetchItems)
  private var lifecycleInitialized = false
  private var disposed = false
  private var renderScheduled = false
  private var measureScheduled = false
  private var itemsObserver: Disposable = VirtualListView.noopDisposable
  private var remoteObserver: Disposable = VirtualListView.noopDisposable
  private var resizeObserver: js.Dynamic | Null = null

  override val element: HTMLDivElement = {
    val divElement = newElement("div")
    divElement.className = "jfx-virtual-list"
    divElement
  }

  def itemsProperty: Property[ListProperty[T]] =
    itemsRefProperty

  def getItems: ListProperty[T] =
    itemsRefProperty.get

  def setItems(items: ListProperty[T]): Unit = {
    val normalized = normalizeItems(items)
    if (!itemsRefProperty.get.eq(normalized)) {
      itemsRefProperty.setAlways(normalized)
    }
  }

  def items: ListProperty[T] =
    getItems

  def items_=(items: ListProperty[T]): Unit =
    setItems(items)

  def refresh(): Unit =
    scheduleRender()

  def scrollTo(index: Int): Unit = {
    if (disposed) return

    val totalCount = maxRenderableCount
    if (totalCount <= 0) return

    val clamped = math.max(0, math.min(totalCount - 1, index))
    viewport.scrollTop = offsetFor(clamped).toDouble
    scheduleRender()
  }

  override protected def mountContent(): Unit = {
    initializeLifecycle()
    scheduleRender()
    scheduleInitialRender(60)
  }

  override def dispose(): Unit = {
    if (disposed) return
    disposed = true

    itemsObserver.dispose()
    remoteObserver.dispose()
    disconnectResizeObserver()
    disposeSlots()

    super.dispose()
  }

  override private[jfx] def childComponentsIterator: Iterator[NodeComponent[? <: Node]] =
    slotPool.iterator

  private def initializeLifecycle(): Unit =
    if (!lifecycleInitialized) {
      lifecycleInitialized = true
      initializeStructure()
      initializeObservers()
    }

  private def initializeStructure(): Unit = {
    viewport.className = "jfx-virtual-list-viewport"
    viewport.style.height = "100%"
    viewport.style.width = "100%"
    viewport.style.overflow = "auto"
    viewport.style.position = "relative"

    content.className = "jfx-virtual-list-content"
    content.style.position = "relative"
    content.style.width = "100%"
    content.style.minHeight = "100%"

    viewport.appendChild(content)
    element.appendChild(viewport)

    resizeObserver = createResizeObserver()
    observeResize(element)
    observeResize(viewport)
  }

  private def initializeObservers(): Unit = {
    val scrollListener: js.Function1[Event, Unit] = _ => scheduleRender()
    viewport.addEventListener("scroll", scrollListener)
    addDisposable(() => viewport.removeEventListener("scroll", scrollListener))

    val resizeHandling = installResizeHandling()
    addDisposable(resizeHandling)

    addDisposable(
      itemsRefProperty.observe { _ =>
        resetMeasurements()
        invalidateSlots()
        rewireItemsObserver()
        scheduleRender()
      }
    )

    rewireItemsObserver()
  }

  private def rewireItemsObserver(): Unit = {
    itemsObserver.dispose()
    remoteObserver.dispose()

    val items = getItems
    val remote = currentRemoteItems

    itemsObserver =
      items.observeChanges { change =>
        if (remote == null) {
          handleLocalItemsChange(change)
        } else {
          invalidateSlots()
          scheduleRender()
        }
      }

    remoteObserver =
      if (remote == null) {
        VirtualListView.noopDisposable
      } else {
        val composite = CompositeDisposable()
        composite.add(remote.loadingProperty.observe(_ => scheduleRender()))
        composite.add(remote.errorProperty.observe(_ => scheduleRender()))
        composite.add(remote.totalCountProperty.observe(_ => scheduleRender()))
        composite.add(remote.hasMoreProperty.observe(_ => scheduleRender()))
        composite.add(remote.nextQueryProperty.observe(_ => scheduleRender()))
        composite.add(
          remote.queryProperty.observeWithoutInitial { _ =>
            resetMeasurements()
            invalidateSlots()
            scheduleRender()
          }
        )
        composite.add(
          remote.sortingProperty.observeWithoutInitial { _ =>
            resetMeasurements()
            invalidateSlots()
            scheduleRender()
          }
        )

        if (remote.length == 0 && !remote.loadingProperty.get && remote.errorProperty.get.isEmpty) {
          discardPromise(remote.reload())
        }

        composite
      }
  }

  private def handleLocalItemsChange(change: Change[T]): Unit = {
    change match {
      case UpdateAt(_, _, _, _) =>
        invalidateSlots()
      case Add(_, _) =>
        if (heights.length > getItems.length) resetMeasurements()
      case _ =>
        resetMeasurements()
    }

    scheduleRender()
  }

  private def render(): Unit = {
    if (disposed) return

    val viewportHeight = math.max(viewport.clientHeight, 0)
    if (viewportHeight <= 0) return

    rebuildPrefixIfDirty()
    updateContentHeight()

    val maxCount = maxRenderableCount
    if (maxCount <= 0) {
      hideUnusedSlots(from = 0, clearContent = true)
      return
    }

    val scrollTop = viewport.scrollTop.toInt
    val startOffset = math.max(0, scrollTop - overscanPx)
    val endOffset = scrollTop + viewportHeight + overscanPx

    val maxSlots = maxSlotsForViewport(viewportHeight)
    val visible = mutable.ArrayBuffer.empty[(Int, Int)]

    var index = indexForOffset(startOffset)
    var top = offsetFor(index)

    while (top < endOffset && index < maxCount && visible.length < maxSlots) {
      visible += ((index, top))
      top += heightFor(index)
      index += 1
    }

    if (visible.isEmpty) {
      hideUnusedSlots(from = 0, clearContent = true)
      return
    }

    ensureSlotCount(visible.length)

    var anyRendered = false

    visible.zipWithIndex.foreach { case ((rowIndex, topPx), poolIndex) =>
      val slot = slotPool(poolIndex)
      val rowItem = itemAt(rowIndex)
      val loaded = rowItem != null

      slot.element.style.top = s"${topPx}px"
      slot.element.style.display = ""
      slot.element.classList.remove("is-hidden")

      if (slot.boundIndex != rowIndex || slot.loaded != loaded || !sameRenderedItem(slot.renderedItem, rowItem)) {
        slot.setContent(buildRenderedContent(rowItem, rowIndex))
        slot.boundIndex = rowIndex
        slot.loaded = loaded
        slot.renderedItem = rowItem.asInstanceOf[Any]
        anyRendered = true
      }
    }

    hideUnusedSlots(from = visible.length)

    if (anyRendered) {
      scheduleMeasure()
    }

    requestMoreIfNecessary(visible.head._1, visible.last._1 + 1)
  }

  private def ensureSlotCount(required: Int): Unit = {
    while (slotPool.length < required) {
      val slot = new SlotHost()
      content.appendChild(slot.element)
      slot.parent = Some(this)
      if (isMounted) {
        slot.onMount()
        registerSubtree(slot)
      }
      observeResize(slot.element)
      slotPool += slot
    }

    while (slotPool.length > required) {
      val slot = slotPool.remove(slotPool.length - 1)
      if (slot.parent.contains(this) && slot.isMounted) {
        unregisterSubtree(slot)
        slot.onUnmount()
      }
      slot.parent = None
      removeDomNode(slot.element)
      slot.dispose()
    }
  }

  private def hideUnusedSlots(from: Int, clearContent: Boolean = false): Unit =
    (from until slotPool.length).foreach { index =>
      val slot = slotPool(index)
      slot.element.style.display = "none"
      slot.element.classList.add("is-hidden")
      slot.boundIndex = -1
      slot.loaded = false
      slot.renderedItem = null
      if (clearContent) slot.setContent(null)
    }

  private def disposeSlots(): Unit = {
    slotPool.foreach { slot =>
      if (slot.parent.contains(this) && slot.isMounted) {
        unregisterSubtree(slot)
        slot.onUnmount()
      }
      slot.parent = None
      removeDomNode(slot.element)
      slot.dispose()
    }
    slotPool.clear()
  }

  private def buildRenderedContent(item: T | Null, index: Int): NodeComponent[? <: Node] | Null =
    DslRuntime.withScope(renderScope) {
      DslRuntime.withComponentContext(ComponentContext(None, renderEnclosingForm)) {
        given Scope = renderScope
        renderer(item, index)
      }
    }

  private def scheduleRender(): Unit = {
    if (disposed || renderScheduled) return
    renderScheduled = true
    window.requestAnimationFrame { _ =>
      renderScheduled = false
      render()
    }
  }

  private def scheduleInitialRender(remainingFrames: Int): Unit = {
    if (disposed || remainingFrames <= 0) return
    window.requestAnimationFrame { _ =>
      if (!disposed) {
        render()
        if (!element.isConnected || viewport.clientHeight == 0) {
          scheduleInitialRender(remainingFrames - 1)
        }
      }
    }
  }

  private def scheduleMeasure(): Unit = {
    if (disposed || measureScheduled) return
    measureScheduled = true
    window.requestAnimationFrame { _ =>
      measureScheduled = false
      if (disposed) {
        ()
      } else {
        var changed = false

        slotPool.foreach { slot =>
          if (slot.boundIndex >= 0 && slot.loaded) {
            val height = slot.element.offsetHeight.toInt
            if (height > 0 && updateHeight(slot.boundIndex, height)) {
              changed = true
            }
          }
        }

        if (changed) {
          rebuildPrefixIfDirty()
          updateContentHeight()
          render()
        }
      }
    }
  }

  private def currentRemoteItems: RemoteListProperty[T, ?] | Null =
    getItems.remotePropertyOrNull

  private def requestMoreIfNecessary(visibleStartIndex: Int, visibleEndExclusive: Int): Unit = {
    val remote = currentRemoteItems
    if (remote == null || remote.loadingProperty.get || remote.errorProperty.get.nonEmpty) return

    if (knownItemCount.isEmpty && canStillGrow) {
      val projectedEnd = getItems.length + tailPaddingItems
      if (visibleEndExclusive + prefetchItems > projectedEnd) {
        tailPaddingItems += math.max(prefetchItems * 2, prefetchItems)
        updateContentHeight()
      }
    }

    if (remote.supportsRangeLoading) {
      val requestFrom = math.max(0, visibleStartIndex - prefetchItems)
      val requestToExclusive = visibleEndExclusive

      if (!remote.isRangeLoaded(requestFrom, requestToExclusive)) {
        discardPromise(remote.ensureRangeLoaded(requestFrom, requestToExclusive))
      }
    } else if (canStillGrow) {
      val loadedLength = getItems.length
      val threshold = math.max(1, prefetchItems / 2)
      if (loadedLength == 0) {
        discardPromise(remote.reload())
      } else if (visibleEndExclusive >= math.max(0, loadedLength - threshold)) {
        discardPromise(remote.loadMore())
      }
    }
  }

  private def itemAt(index: Int): T | Null =
    currentRemoteItems match {
      case null =>
        if (index >= 0 && index < getItems.length) getItems(index)
        else null
      case remote =>
        remote.getLoadedItem(index).orNull
    }

  private def knownItemCount: Option[Int] =
    currentRemoteItems match {
      case null   => Some(getItems.length)
      case remote => remote.totalCountProperty.get
    }

  private def canStillGrow: Boolean =
    currentRemoteItems match {
      case null =>
        false
      case remote =>
        remote.loadingProperty.get ||
        remote.hasMoreProperty.get ||
        remote.nextQueryProperty.get.nonEmpty ||
        remote.totalCountProperty.get.isEmpty
    }

  private def maxRenderableCount: Int =
    knownItemCount.getOrElse {
      if (shouldRenderUnloadedPlaceholders) getItems.length + tailPaddingItems
      else 0
    }

  private def resetMeasurements(): Unit = {
    heights.clear()
    prefix.clear()
    prefix += 0
    prefixDirtyFrom = Int.MaxValue
    tailPaddingItems = math.max(prefetchItems * 3, prefetchItems)
  }

  private def invalidateSlots(): Unit =
    slotPool.foreach { slot =>
      slot.boundIndex = -1
      slot.loaded = false
      slot.renderedItem = null
    }

  private def ensureHeightsSize(size: Int): Unit =
    while (heights.length < size) {
      heights += estimateHeightPx
      prefix += (prefix.last + estimateHeightPx)
    }

  private def updateHeight(index: Int, newHeight: Int): Boolean = {
    if (index < 0) return false

    ensureHeightsSize(index + 1)
    val previous = heights(index)
    if (previous == newHeight) return false

    heights(index) = newHeight
    prefixDirtyFrom = math.min(prefixDirtyFrom, index + 1)
    true
  }

  private def rebuildPrefixIfDirty(): Unit = {
    val from = prefixDirtyFrom
    if (from == Int.MaxValue) return

    val start = math.max(1, math.min(from, prefix.length - 1))
    var index = start
    while (index < prefix.length) {
      prefix(index) = prefix(index - 1) + heights(index - 1)
      index += 1
    }

    prefixDirtyFrom = Int.MaxValue
  }

  private def offsetFor(index: Int): Int = {
    val loaded = heights.length
    if (index <= loaded) {
      if (index < prefix.length) prefix(index) else prefix.last
    } else {
      prefix.last + (index - loaded) * estimateHeightPx
    }
  }

  private def indexForOffset(offset: Int): Int = {
    val normalizedOffset = math.max(0, offset)
    val loaded = heights.length
    if (loaded == 0) return 0

    val totalKnownHeight = prefix.last
    if (normalizedOffset >= totalKnownHeight) {
      loaded + ((normalizedOffset - totalKnownHeight) / estimateHeightPx)
    } else {
      var low = 0
      var high = loaded

      while (low < high) {
        val mid = (low + high) / 2
        if (prefix(mid + 1) <= normalizedOffset) low = mid + 1
        else high = mid
      }

      low
    }
  }

  private def heightFor(index: Int): Int =
    if (index >= 0 && index < heights.length) heights(index)
    else estimateHeightPx

  private def shouldRenderUnloadedPlaceholders: Boolean =
    getItems.length > 0 || knownItemCount.exists(_ > 0)

  private def updateContentHeight(): Unit = {
    rebuildPrefixIfDirty()

    if (!shouldRenderUnloadedPlaceholders) {
      content.style.height = "0px"
      return
    }

    val base = prefix.lastOption.getOrElse(0)
    val extra =
      knownItemCount match {
        case Some(total) =>
          math.max(0, total - heights.length) * estimateHeightPx
        case None if canStillGrow =>
          tailPaddingItems * estimateHeightPx
        case None =>
          0
      }

    content.style.height = s"${base + extra}px"
  }

  private def maxSlotsForViewport(viewportHeight: Int): Int = {
    val minRowHeight = math.max(12, math.min(estimateHeightPx, math.max(estimateHeightPx / 2, 1)))
    val area = viewportHeight + 2 * overscanPx
    val raw = (area / minRowHeight) + 8
    math.min(600, math.max(32, raw))
  }

  private def installResizeHandling(): Disposable = {
    val composite = CompositeDisposable()

    val listener: js.Function1[Event, Unit] = _ => scheduleRender()
    window.addEventListener("resize", listener)
    composite.add(() => window.removeEventListener("resize", listener))

    composite
  }

  private def createResizeObserver(): js.Dynamic | Null = {
    val resizeObserverCtor = js.Dynamic.global.ResizeObserver
    if (js.isUndefined(resizeObserverCtor) || resizeObserverCtor == null) {
      null
    } else {
      js.Dynamic.newInstance(resizeObserverCtor)((_: js.Any, _: js.Any) => scheduleMeasure())
    }
  }

  private def observeResize(target: org.scalajs.dom.Element): Unit =
    if (resizeObserver != null) {
      resizeObserver.nn.observe(target)
    }

  private def disconnectResizeObserver(): Unit =
    if (resizeObserver != null) {
      resizeObserver.nn.disconnect()
      resizeObserver = null
    }

  private def removeDomNode(node: Node): Unit = {
    val parent = node.parentNode
    if (parent != null) parent.removeChild(node)
  }

  private def normalizeItems(items: ListProperty[T] | Null): ListProperty[T] =
    if (items == null) ListProperty()
    else items

  private def discardPromise(promise: js.Promise[?]): Unit = {
    promise.toFuture.recover { case _ => () }
    ()
  }

  private def sameRenderedItem(left: Any, right: Any): Boolean =
    (left, right) match {
      case (null, null) =>
        true
      case (leftRef: AnyRef, rightRef: AnyRef) =>
        leftRef.eq(rightRef)
      case _ =>
        left == right
    }

  private final class SlotHost extends ManagedElementComponent[HTMLDivElement] {

    private var currentContent: NodeComponent[? <: Node] | Null = null

    var boundIndex: Int = -1
    var loaded: Boolean = false
    var renderedItem: Any = uninitialized

    override val element: HTMLDivElement = {
      val divElement = newElement("div")
      divElement.className = "jfx-virtual-list-cell"
      divElement.style.position = "absolute"
      divElement.style.left = "0"
      divElement.style.width = "100%"
      divElement
    }

    def setContent(next: NodeComponent[? <: Node] | Null): Unit = {
      if ((currentContent eq next) && next != null) return

      clearChildren()
      currentContent = next

      if (next != null) {
        addChild(next)
      }
    }
  }
}

object VirtualListView {

  type Renderer[T] = (T | Null, Int) => NodeComponent[? <: Node] | Null

  private[control] val noopDisposable: Disposable = () => ()

  def virtualList[T](
    items: ListProperty[T],
    estimateHeightPx: Int = 44,
    overscanPx: Int = 240,
    prefetchItems: Int = 80
  )(renderer: Renderer[T]): VirtualListView[T] =
    DslRuntime.currentScope { currentScope =>
      val currentContext = DslRuntime.currentComponentContext()
      val component =
        new VirtualListView[T](
          initialItems = items,
          estimateHeightPx = estimateHeightPx,
          overscanPx = overscanPx,
          prefetchItems = prefetchItems,
          renderScope = currentScope,
          renderEnclosingForm = currentContext.enclosingForm,
          renderer = renderer
        )

      DslRuntime.attach(component, currentContext)
      component
    }
}
