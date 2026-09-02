package jfx.control.virtualized

/**
 * Wo liegt Element `index`, und welche Elemente sind sichtbar?
 *
 * Das ist der einzige echte Unterschied zwischen TableView, DataGrid und
 * VirtualListView. Alles andere -- Scroll-Zustand, Messung, Remote-Anbindung,
 * Crawl-Zustand -- war dreimal dieselbe Logik und liegt jetzt in
 * [[VirtualizedCollection]] beziehungsweise [[CrawlableCollection]].
 *
 * Die Achse ist zweidimensional, nicht nur "feste gegen gemessene Hoehe":
 *
 * {{{
 *                    Hoehe                Spalten   Ueberhang
 *   TableView        fix (rowHeight)      1         overscanRows (Konstante)
 *   DataGrid         fix (itemHeight+gap) N         overscanRows (Property)
 *   VirtualListView  gemessen             1         overscanPx   (Property)
 * }}}
 *
 * Implementierungen duerfen Zustand halten -- [[MeasuredRowGeometry]] traegt die
 * gemessenen Hoehen und deren Praefixsummen.
 *
 * Siehe jfx-controls/VIRTUALIZATION.md und CHANGE.md P3-1.
 */
trait ItemGeometry {

  /** Abstand oberhalb des ersten Elements, etwa durch einen Header. */
  def headerOffset: Double

  /** Obere Kante von Element `index`, inklusive [[headerOffset]]. */
  def topForIndex(index: Int): Double

  /**
   * Index des Elements an der Scroll-Position `offset`, wobei `offset` bereits
   * um [[headerOffset]] bereinigt ist.
   */
  def indexForOffset(offset: Double): Int

  /** Gesamthoehe des Inhalts fuer `total` Elemente, ohne [[headerOffset]]. */
  def contentHeight(total: Int): Double

  /**
   * Sichtbarer Bereich als `[start, end)`.
   *
   * Nur der Nicht-Crawl-Fall: der Crawl-Zweig ist in allen drei Controls
   * wortgleich und liegt in [[VirtualizedCollection.visibleRange]].
   */
  def visibleRange(total: Int, scrollTop: Double, viewportHeight: Double): (Int, Int)
}

/**
 * Feste Zeilenhoehe, eine Spalte -- das Modell von TableView.
 *
 * Der Ueberhang ist in Zeilen angegeben.
 */
final class FixedRowGeometry(
    rowHeight: () => Double,
    headerHeightValue: () => Double,
    overscanRows: Int
) extends ItemGeometry {

  private def effectiveRowHeight: Double = math.max(1.0, rowHeight())

  override def headerOffset: Double = headerHeightValue()

  override def topForIndex(index: Int): Double =
    headerOffset + math.max(0, index) * effectiveRowHeight

  override def indexForOffset(offset: Double): Int =
    math.max(0, math.floor(math.max(0.0, offset) / effectiveRowHeight).toInt)

  override def contentHeight(total: Int): Double =
    math.max(0, total) * effectiveRowHeight

  override def visibleRange(
      total: Int,
      scrollTop: Double,
      viewportHeight: Double
  ): (Int, Int) = {
    val rowHeightValue     = effectiveRowHeight
    val effectiveScrollTop = math.max(0.0, scrollTop - headerOffset)
    val firstVisible       = math.min(total - 1, math.floor(effectiveScrollTop / rowHeightValue).toInt)
    val visibleCount       = math.ceil(math.max(1.0, viewportHeight) / rowHeightValue).toInt + 1

    (
      math.max(0, firstVisible - overscanRows),
      math.min(total, firstVisible + visibleCount + overscanRows)
    )
  }
}

/**
 * Feste Zellengroesse in einem Raster mit N Spalten -- das Modell von DataGrid.
 *
 * Der Ueberhang ist in Zeilen angegeben und wird auf ganze Zeilen gerundet: es
 * werden immer vollstaendige Rasterzeilen sichtbar gemacht.
 */
final class GridGeometry(
    columnCount: () => Int,
    rowStep: () => Double,
    contentTopOffset: () => Double,
    overscanRows: () => Int
) extends ItemGeometry {

  private def effectiveColumns: Int    = math.max(1, columnCount())
  private def effectiveRowStep: Double = math.max(1.0, rowStep())

  /** Anzahl Rasterzeilen fuer `total` Elemente. */
  private def rowCountFor(total: Int): Int = {
    val columns = effectiveColumns
    if (total <= 0) 0 else (total + columns - 1) / columns
  }

  override def headerOffset: Double = contentTopOffset()

  override def topForIndex(index: Int): Double =
    headerOffset + (math.max(0, index) / effectiveColumns) * effectiveRowStep

  override def indexForOffset(offset: Double): Int = {
    val row = math.max(0, math.floor(math.max(0.0, offset) / effectiveRowStep).toInt)
    row * effectiveColumns
  }

  override def contentHeight(total: Int): Double =
    rowCountFor(total) * effectiveRowStep

  override def visibleRange(
      total: Int,
      scrollTop: Double,
      viewportHeight: Double
  ): (Int, Int) = {
    val columns            = effectiveColumns
    val step               = effectiveRowStep
    val rows               = rowCountFor(total)
    val effectiveScrollTop = math.max(0.0, scrollTop - headerOffset)
    val firstVisibleRow    =
      math.min(math.max(0, rows - 1), math.floor(effectiveScrollTop / step).toInt)
    val visibleRows = math.ceil(math.max(1.0, viewportHeight) / step).toInt + 1
    val overscan    = math.max(0, overscanRows())
    val startRow    = math.max(0, firstVisibleRow - overscan)
    val endRow      = math.min(rows, firstVisibleRow + visibleRows + overscan)

    (math.min(total, startRow * columns), math.min(total, endRow * columns))
  }
}

/**
 * Gemessene Hoehen, eine Spalte -- das Modell von VirtualListView.
 *
 * Traegt die gemessenen Hoehen und deren Praefixsummen. Solange eine Zeile nicht
 * gemessen wurde, gilt die Schaetzung; `prefixDirtyFrom` merkt sich, ab wo die
 * Summen neu gebildet werden muessen, damit eine Messung nicht die ganze Liste
 * neu berechnet.
 *
 * Der Ueberhang ist in Pixeln angegeben, nicht in Zeilen -- bei variablen Hoehen
 * ist eine Zeilenzahl keine sinnvolle Groesse.
 */
final class MeasuredRowGeometry(
    estimateHeight: () => Double,
    headerHeightValue: () => Double,
    overscanPx: () => Double,
    maxSlots: Double => Int
) extends ItemGeometry {

  private val heights = scala.collection.mutable.ArrayBuffer.empty[Double]
  private val prefix  = scala.collection.mutable.ArrayBuffer(0.0)

  private var prefixDirtyFrom = Int.MaxValue

  private def estimate: Double = math.max(1.0, estimateHeight())

  override def headerOffset: Double = headerHeightValue()

  def size: Int = heights.length

  def ensureSize(total: Int): Unit = {
    while (heights.length < total) {
      heights += estimate
      markDirtyFrom(heights.length - 1)
    }
    if (heights.length > total) {
      heights.remove(total, heights.length - total)
      markDirtyFrom(total)
    }
  }

  def heightFor(index: Int): Double =
    if (index >= 0 && index < heights.length) heights(index) else estimate

  /** Traegt eine gemessene Hoehe ein. Liefert true, wenn sie sich geaendert hat. */
  def setHeight(index: Int, value: Double): Boolean =
    if (index < 0 || index >= heights.length || math.abs(heights(index) - value) < 0.5) false
    else {
      heights(index) = value
      markDirtyFrom(index)
      true
    }

  /** Setzt alle Hoehen auf die Schaetzung zurueck. */
  def resetHeights(): Unit = {
    var index = 0
    while (index < heights.length) {
      heights(index) = estimate
      index += 1
    }
    markDirtyFrom(0)
  }

  def clear(): Unit = {
    heights.clear()
    prefix.clear()
    prefix += 0.0
    prefixDirtyFrom = Int.MaxValue
  }

  private def markDirtyFrom(index: Int): Unit =
    prefixDirtyFrom = math.min(prefixDirtyFrom, math.max(0, index))

  private def rebuildPrefix(): Unit = {
    if (prefixDirtyFrom == Int.MaxValue && prefix.length == heights.length + 1) return

    val from = math.min(prefixDirtyFrom, math.max(0, prefix.length - 1))
    while (prefix.length > from + 1) prefix.remove(prefix.length - 1)

    var index = from
    while (index < heights.length) {
      prefix += prefix(index) + heights(index)
      index += 1
    }

    prefixDirtyFrom = Int.MaxValue
  }

  /** Abstand vom ersten Element bis zur oberen Kante von `index`. */
  def offsetFor(index: Int): Double = {
    rebuildPrefix()
    val bounded = math.max(0, math.min(index, prefix.length - 1))
    prefix(bounded)
  }

  override def topForIndex(index: Int): Double =
    headerOffset + offsetFor(math.max(0, index))

  override def indexForOffset(offset: Double): Int = {
    rebuildPrefix()

    val target = math.max(0.0, offset)
    var low    = 0
    var high   = math.max(0, prefix.length - 2)

    while (low < high) {
      val mid = (low + high + 1) >>> 1
      if (prefix(mid) <= target) low = mid else high = mid - 1
    }

    low
  }

  override def contentHeight(total: Int): Double = {
    rebuildPrefix()
    val bounded = math.max(0, math.min(total, prefix.length - 1))
    prefix(bounded)
  }

  override def visibleRange(
      total: Int,
      scrollTop: Double,
      viewportHeight: Double
  ): (Int, Int) = {
    val height          = math.max(1.0, viewportHeight)
    val overscan        = math.max(0.0, overscanPx())
    val effectiveScroll = math.max(0.0, scrollTop - headerOffset)
    val startOffset     = math.max(0.0, effectiveScroll - overscan)
    val endOffset       = effectiveScroll + height + overscan
    val start           = math.max(0, math.min(indexForOffset(startOffset), math.max(0, total - 1)))
    val maximum         = maxSlots(height)

    var index = start
    var top   = offsetFor(index)
    while (index < total && top < endOffset && index - start < maximum) {
      top += heightFor(index)
      index += 1
    }

    (start, math.max(start, index))
  }
}
