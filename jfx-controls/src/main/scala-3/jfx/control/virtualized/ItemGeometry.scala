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
 * Der Ueberhang ist in Zeilen angegeben: es werden immer vollstaendige
 * Rasterzeilen sichtbar gemacht.
 *
 * itemHeight und gap stehen einzeln und nicht als fertiger rowStep, weil die
 * Gesamthoehe die Luecken zwischen den Zeilen zaehlt und nicht hinter der
 * letzten: `rows * itemHeight + (rows - 1) * gap`. Mit rowStep waere eine Luecke
 * zu viel drin.
 */
final class GridGeometry(
    columnCount: () => Int,
    itemHeight: () => Double,
    gap: () => Double,
    contentTopOffset: () => Double,
    overscanRows: () => Int
) extends ItemGeometry {

  private def effectiveColumns: Int    = math.max(1, columnCount())
  private def effectiveRowStep: Double = math.max(1.0, itemHeight() + gap())

  /** Anzahl Rasterzeilen fuer `total` Elemente. */
  private def rowCountFor(total: Int): Int =
    if (total <= 0) 0 else math.ceil(total.toDouble / effectiveColumns).toInt

  override def headerOffset: Double = contentTopOffset()

  override def topForIndex(index: Int): Double =
    headerOffset + math.max(0, index) / effectiveColumns * effectiveRowStep

  override def indexForOffset(offset: Double): Int = {
    val row = math.max(0, math.floor(math.max(0.0, offset) / effectiveRowStep).toInt)
    row * effectiveColumns
  }

  override def contentHeight(total: Int): Double = {
    val rows = rowCountFor(total)
    if (rows <= 0) 0.0 else rows * itemHeight() + math.max(0, rows - 1) * gap()
  }

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
 * Traegt die gemessenen Hoehen und deren Praefixsummen. Was noch nicht gemessen
 * wurde, gilt mit der Schaetzung; `prefixDirtyFrom` merkt sich, ab wo die Summen
 * neu gebildet werden muessen, damit eine einzelne Messung nicht die ganze Liste
 * neu berechnet.
 *
 * Ueber den gemessenen Bereich hinaus wird mit der Schaetzung extrapoliert --
 * `renderableCount` darf groesser sein als die Zahl gemessener Zeilen, etwa
 * durch die Tail-Padding-Reserve beim Nachladen.
 *
 * Der Ueberhang ist in Pixeln angegeben, nicht in Zeilen: bei variablen Hoehen
 * ist eine Zeilenzahl keine sinnvolle Groesse.
 */
final class MeasuredRowGeometry(
    estimateHeight: () => Double,
    headerHeightValue: () => Double,
    overscanPx: () => Double
) extends ItemGeometry {

  private val heights = scala.collection.mutable.ArrayBuffer.empty[Double]
  private val prefix  = scala.collection.mutable.ArrayBuffer(0.0)

  private var prefixDirtyFrom = Int.MaxValue

  private def estimate: Double = estimateHeight()

  override def headerOffset: Double = headerHeightValue()

  def measuredCount: Int = heights.length

  def ensureSize(size: Int): Unit =
    while (heights.length < size) {
      heights += estimate
      prefix += prefix.last + estimate
    }

  def heightFor(index: Int): Double =
    heights.lift(index).getOrElse(estimate)

  /**
   * Traegt eine gemessene Hoehe ein und liefert die Differenz zur bisherigen,
   * oder None, wenn sie sich nicht nennenswert geaendert hat.
   */
  def updateHeight(index: Int, newHeight: Double): Option[Double] =
    if (index < 0) None
    else {
      ensureSize(index + 1)
      val previous = heights(index)
      val delta    = newHeight - previous
      if (math.abs(delta) <= 0.5) None
      else {
        heights(index) = newHeight
        prefixDirtyFrom = math.min(prefixDirtyFrom, index + 1)
        Some(delta)
      }
    }

  def rebuildPrefixIfDirty(): Unit =
    if (prefixDirtyFrom != Int.MaxValue) {
      var index = math.max(1, math.min(prefixDirtyFrom, prefix.length - 1))
      while (index < prefix.length) {
        prefix(index) = prefix(index - 1) + heights(index - 1)
        index += 1
      }
      prefixDirtyFrom = Int.MaxValue
    }

  def clear(): Unit = {
    heights.clear()
    prefix.clear()
    prefix += 0.0
    prefixDirtyFrom = Int.MaxValue
  }

  /** Abstand vom ersten Element bis zur oberen Kante von `index`, ohne Header. */
  def offsetFor(index: Int): Double = {
    val loaded = heights.length
    if (index <= loaded) prefix.lift(index).getOrElse(prefix.last)
    else prefix.last + (index - loaded) * estimate
  }

  override def topForIndex(index: Int): Double =
    headerOffset + offsetFor(math.max(0, index))

  override def indexForOffset(offset: Double): Int = {
    val normalized = math.max(0.0, offset)
    val loaded     = heights.length

    if (loaded == 0) math.floor(normalized / estimate).toInt
    else if (normalized >= prefix.last)
      loaded + math.floor((normalized - prefix.last) / estimate).toInt
    else {
      var low  = 0
      var high = loaded
      while (low < high) {
        val middle = (low + high) / 2
        if (prefix(middle + 1) <= normalized) low = middle + 1
        else high = middle
      }
      low
    }
  }

  override def contentHeight(total: Int): Double = {
    rebuildPrefixIfDirty()
    val measured      = math.min(total, heights.length)
    val measuredHeight = prefix.lift(measured).getOrElse(0.0)
    measuredHeight + math.max(0, total - measured) * estimate
  }

  /**
   * Obergrenze fuer die Zahl gleichzeitig gemounteter Zeilen.
   *
   * Ohne sie wuerde eine Liste, deren Zeilen alle deutlich niedriger als die
   * Schaetzung ausfallen, beliebig viele Zeilen in den sichtbaren Bereich
   * ziehen.
   */
  private def maxSlotsForViewport(viewportHeight: Double): Int = {
    val minimum = math.max(12.0, math.min(estimate, math.max(estimate / 2.0, 1.0)))
    val area    = viewportHeight + 2 * math.max(0.0, overscanPx())
    math.min(600, math.max(32, math.ceil(area / minimum).toInt + 8))
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
    val start           = math.max(0, math.min(indexForOffset(startOffset), total - 1))
    val maximum         = maxSlotsForViewport(height)

    var index = start
    var top   = offsetFor(index)
    while (index < total && top < endOffset && index - start < maximum) {
      top += heightFor(index)
      index += 1
    }

    (start, math.max(start + 1, index).min(total))
  }
}
