package jfx.control.virtualized

import jfx.core.component.AbstractComponent
import jfx.core.layout.Div
import jfx.core.remote.RemoteListProperty
import jfx.core.render.DomHostElement
import jfx.core.state.{CompositeDisposable, Disposable, ListProperty, Property}
import org.scalajs.dom

import scala.concurrent.Future

/**
 * Gemeinsame Basis von TableView, DataGrid und VirtualListView.
 *
 * Vor P3-1 teilten sich die drei rund 70 bis 100 identisch benannte Member --
 * Scroll-Zustand, Viewport-Messung, Remote-Anbindung, Item-Zustand,
 * Revisionszaehler und DOM-Zugriff lagen dreimal parallel. Jede Korrektur musste
 * dreimal gemacht werden; bei `requestLazyLoadIfNecessary` wurde sie es nicht
 * (siehe jfx-controls/VIRTUALIZATION.md).
 *
 * Was die drei tatsaechlich unterscheidet, steckt in [[ItemGeometry]].
 *
 * Die Unterklasse liefert:
 *   - [[geometry]]           -- wo liegt Element i, was ist sichtbar
 *   - [[renderableCount]]    -- wie viele Elemente insgesamt darstellbar sind
 *   - [[recomputeVisible]]   -- den controlspezifischen Neuaufbau der Sichtliste
 *   - [[handleLocalItemsChange]] und [[resetMeasurements]]
 */
abstract class VirtualizedCollection[T] extends AbstractComponent {

  // --- von der Unterklasse zu liefern -------------------------------------

  protected def geometry: ItemGeometry

  /**
   * Anzahl darstellbarer Elemente. Bei einer Remote-Liste ist das die
   * Gesamtzahl, nicht die Zahl der bereits geladenen.
   */
  protected def renderableCount: Int

  /** Baut die controlspezifische Liste sichtbarer Elemente neu auf. */
  protected def recomputeVisible(): Unit

  /** Reaktion auf eine Aenderung einer lokalen (nicht remote) Liste. */
  protected def handleLocalItemsChange(change: ListProperty.Change[T]): Unit

  /** Verwirft zwischengespeicherte Messungen; Standard: nichts zu tun. */
  protected def resetMeasurements(): Unit = ()

  /** Darf die Liste noch wachsen? Steuert das Nachladen am Ende. */
  protected def canStillGrow: Boolean =
    currentRemoteItems match {
      case null   => false
      case remote => remote.hasMoreProperty.get || remote.nextQueryProperty.get.nonEmpty
    }

  // --- Zustand -------------------------------------------------------------

  protected val itemsRefProperty: Property[ListProperty[T]] = Property(ListProperty[T]())

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

  // --- Items ---------------------------------------------------------------

  def itemsProperty: Property[ListProperty[T]] = itemsRefProperty
  def getItems: ListProperty[T]                = itemsRefProperty.get

  def setItems(value: ListProperty[T]): Unit = {
    val normalized = Option(value).getOrElse(ListProperty[T]())
    if (!itemsRefProperty.get.eq(normalized)) itemsRefProperty.setAlways(normalized)
  }

  /**
   * Die Item-Liste als Remote-Liste, oder null.
   *
   * Frueher fragte das ListProperty selbst ueber remotePropertyOrNull -- eine
   * Rueckwaerts-Abhaengigkeit vom Allgemeinen aufs Spezielle, entfernt in P2-5.
   * Der Cast ist sicher: getItems ist ListProperty[T], und eine
   * RemoteListProperty, die zugleich ListProperty[T] ist, hat notwendig T als
   * Elementtyp. Nur sehen kann der Compiler das wegen Type Erasure nicht.
   */
  protected def currentRemoteItems: RemoteListProperty[T, ?] | Null =
    getItems match {
      case remote: RemoteListProperty[?, ?] => remote.asInstanceOf[RemoteListProperty[T, ?]]
      case _                                => null
    }

  protected def itemAt(index: Int): Option[T] =
    currentRemoteItems match {
      case null   => Option.when(index >= 0 && index < getItems.length)(getItems(index))
      case remote => remote.getLoadedItem(index)
    }

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

  // --- Revisionszaehler ----------------------------------------------------

  protected def bumpItemState(): Unit =
    itemStateRevisionProperty.setAlways(itemStateRevisionProperty.get + 1)

  protected def bumpRemoteState(): Unit =
    remoteStateRevisionProperty.setAlways(remoteStateRevisionProperty.get + 1)

  protected def refreshItemState(): Unit = {
    bumpItemState()
    recomputeVisible()
  }

  // --- Beobachter ----------------------------------------------------------

  /**
   * Haengt die Beobachter neu an die aktuelle Item-Liste.
   *
   * Aufzurufen, wenn itemsRefProperty auf eine andere Liste zeigt.
   * [[onRemoteSortingChanged]] ist der Haken fuer CrawlableCollection.
   */
  protected def rewireItemsObserver(): Unit = {
    itemsObserver.dispose()
    remoteItemsObserver.dispose()
    bumpRemoteState()

    val remote = currentRemoteItems

    itemsObserver = getItems.observeChanges { change =>
      if (remote == null) handleLocalItemsChange(change)
      else {
        bumpRemoteState()
        refreshItemState()
      }
    }

    currentRemoteItems match {
      case null => remoteItemsObserver = Disposable.empty
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
        composite.add(remote.nextQueryProperty.observe(_ => refreshItemState()))
        composite.add(remote.queryProperty.observeWithoutInitial { _ =>
          resetMeasurements()
          refreshItemState()
        })
        composite.add(remote.sortingProperty.observeWithoutInitial { sorting =>
          resetMeasurements()
          refreshItemState()
          onRemoteSortingChanged(sorting)
        })

        remoteItemsObserver = composite

        if (
          browserRendering && remote.length == 0 &&
          !remote.loadingProperty.get && remote.errorProperty.get.isEmpty
        ) discardResult(remote.reload())
    }

    resetMeasurements()
    refreshItemState()
  }

  /** Haken fuer CrawlableCollection; ohne Crawl-Zustand nichts zu tun. */
  protected def onRemoteSortingChanged(
      sorting: Vector[jfx.core.remote.RemoteSort]
  ): Unit = ()

  // --- Scroll und Messung --------------------------------------------------

  protected def updateScrollState(element: dom.html.Element): Unit = {
    scrollTopProperty.set(element.scrollTop)
    onScrollLeftChanged(element.scrollLeft)
    updateViewportSize(element)
  }

  /** Haken fuer horizontal scrollende Controls; nur TableView braucht ihn. */
  protected def onScrollLeftChanged(scrollLeft: Double): Unit = ()

  protected def updateViewportSize(element: dom.html.Element): Unit =
    if (element.clientHeight > 0) viewportHeightProperty.set(element.clientHeight.toDouble)

  protected def scheduleViewportMeasure(): Unit =
    if (!viewportMeasureScheduled && browserRendering) {
      viewportMeasureScheduled = true
      dom.window.requestAnimationFrame { _ =>
        viewportMeasureScheduled = false
        domElement(viewportComponent).foreach(updateViewportSize)
      }
      ()
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

  /**
   * Sichtbarer Bereich als `[start, end)`.
   *
   * Der vordere Zweig ist in allen drei Controls wortgleich: beim
   * Server-Rendering und waehrend der Hydration bestimmt der Crawl-Zustand den
   * Ausschnitt, nicht die Scroll-Position -- sonst waere der gerenderte
   * Ausschnitt nicht reproduzierbar. Erst der else-Zweig unterscheidet sich, und
   * der liegt in der Geometrie.
   */
  protected def visibleRange(total: Int): (Int, Int) =
    if ((!browserRendering || hydrating) && crawlWindow.nonEmpty) {
      val (offset, limit) = crawlWindow.get
      val start           = math.min(offset, total)
      (start, math.min(total, start + limit))
    } else {
      geometry.visibleRange(total, scrollTopProperty.get, viewportHeightProperty.get)
    }

  /**
   * Der beim Server-Rendering zu zeigende Ausschnitt, oder None.
   * CrawlableCollection ueberschreibt das.
   */
  protected def crawlWindow: Option[(Int, Int)] = None

  // --- Nachladen -----------------------------------------------------------

  /**
   * Faehrt den Prefetch fuer den sichtbaren Bereich.
   *
   * Diese Fassung stammt aus DataGrid und VirtualListView; TableView hatte eine
   * aeltere ohne Prefetch-Fenster und ohne Seiten-Ausrichtung und erbt die
   * Verbesserung hier mit. Siehe VIRTUALIZATION.md.
   */
  protected def requestLazyLoadIfNecessary(start: Int, end: Int): Unit =
    currentRemoteItems match {
      case null                                                                      => ()
      case remote if remote.loadingProperty.get || remote.errorProperty.get.nonEmpty => ()
      case remote if remote.supportsRangeLoading                                     =>
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
        // Deduplizierung liegt in RemoteListProperty: gleichlautende Anfragen
        // teilen sich dort ein Future. Das Control muss darueber nicht Buch
        // fuehren.
        if (pageTo > pageFrom && !remote.isRangeLoaded(pageFrom, pageTo)) {
          discardResult(remote.ensureRangeLoaded(pageFrom, pageTo))
        }
      case remote if canStillGrow =>
        val threshold = math.max(1, prefetchItemsProperty.get / 2)
        if (remote.length == 0) discardResult(remote.reload())
        else if (end >= math.max(0, remote.length - threshold)) discardResult(remote.loadMore())
      case _ => ()
    }

  /**
   * Ein Lade-Future, dessen Ergebnis das Control nicht braucht. Der recover
   * verhindert eine unbehandelte Fehlermeldung -- der Fehler selbst steht in
   * RemoteListProperty.errorProperty und wird von dort gerendert.
   */
  protected def discardResult(result: Future[?]): Unit = {
    result.recover { case _ => () }(using scala.concurrent.ExecutionContext.global)
    ()
  }
}
