package jfx.control.virtualized

import jfx.core.component.AbstractComponent
import jfx.core.layout.Div
import jfx.core.remote.RemoteListDataSource
import jfx.core.render.DomHostElement
import jfx.core.state.{CompositeDisposable, Disposable, ListDataSource, ListProperty, Property}
import org.scalajs.dom

import scala.concurrent.Future

/** Gemeinsame Basis von TableView, DataGrid und VirtualListView.
  *
  * Vor P3-1 teilten sich die drei rund 70 bis 100 identisch benannte Member -- Scroll-Zustand,
  * Viewport-Messung, Remote-Anbindung, Item-Zustand, Revisionszaehler und DOM-Zugriff lagen dreimal
  * parallel. Jede Korrektur musste dreimal gemacht werden; bei `requestLazyLoadIfNecessary` wurde
  * sie es nicht (siehe jfx-controls/VIRTUALIZATION.md).
  *
  * Was die drei tatsaechlich unterscheidet, steckt in [[ItemGeometry]].
  *
  * Die Unterklasse liefert:
  *   - [[geometry]] -- wo liegt Element i, was ist sichtbar
  *   - [[renderableCount]] -- wie viele Elemente insgesamt darstellbar sind
  *   - [[recomputeVisible]] -- den controlspezifischen Neuaufbau der Sichtliste
  *   - [[handleLocalItemsChange]] und [[resetMeasurements]]
  */
abstract class VirtualizedCollection[T](protected val dataSource: ListDataSource[T])
    extends AbstractComponent {

  // --- von der Unterklasse zu liefern -------------------------------------

  protected def geometry: ItemGeometry

  /** Anzahl darstellbarer Elemente. Bei einer Remote-Liste ist das die Gesamtzahl, nicht die Zahl
    * der bereits geladenen.
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
      case remote => remote.canLoadMore
    }

  // --- Zustand -------------------------------------------------------------

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

  /** Die Item-Liste als Remote-Liste, oder null.
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

  /** Haengt die Beobachter an die beim Bau uebergebene Datenquelle. [[onRemoteSortingChanged]] ist
    * der Haken fuer CrawlableCollection.
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
    applyViewportSize(element.clientWidth.toDouble, element.clientHeight.toDouble)

  /** Uebernimmt eine gemessene Viewport-Groesse.
    *
    * Getrennt von [[updateViewportSize]], damit die Uebernahme ohne DOM testbar ist. Der Fehler,
    * den das absichert, sass genau hier: die Basis uebernahm zunaechst nur die Hoehe, weil sie aus
    * VirtualListView stammte. TableView und DataGrid brauchen aber auch die Breite -- ohne sie
    * blieben beide bei ihrem Startwert von 800 stehen, das Grid zeigte eine Spalte zu wenig und die
    * Tabelle verteilte ihre Spaltenbreiten auf eine zu schmale Flaeche.
    */
  private[control] def applyViewportSize(width: Double, height: Double): Unit = {
    if (width > 0) onViewportWidthMeasured(width)
    if (height > 0) viewportHeightProperty.set(height)
  }

  /** Haken fuer Controls mit horizontaler Ausdehnung. VirtualListView ist einspaltig und braucht
    * die Breite nicht.
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

  /** Der Rumpf einer Viewport-Messung: Groesse uebernehmen, dann die Nachlaeufer benachrichtigen.
    *
    * Vom requestAnimationFrame getrennt, damit die Verkettung ohne DOM pruefbar ist. Genau hier
    * sass ein Fehler: beim Zusammenlegen in P3-1 ist der zweite Schritt verloren gegangen, wodurch
    * die gespeicherte Scroll-Position nie wiederhergestellt wurde und hydrating dauerhaft true
    * blieb -- was wiederum das Nachladen blockierte.
    */
  private[control] def measureViewport(width: Double, height: Double): Unit = {
    applyViewportSize(width, height)
    onViewportMeasured()
  }

  /** Haken nach einer Viewport-Messung. CrawlableCollection stellt darueber die gespeicherte
    * Scroll-Position wieder her und gibt die Hydration frei.
    */
  protected def onViewportMeasured(): Unit = ()

  /** Haengt die Item-Beobachter an und sorgt dafuer, dass sie beim Entsorgen der Komponente wieder
    * abgehen. Von installObservers der Unterklasse aufzurufen.
    */
  protected def installItemObservers(): Unit = {
    addDisposable(Disposable {
      itemsObserver.dispose()
      remoteItemsObserver.dispose()
    })
    wireItemsObserver()
  }

  /** Beobachtet die Groesse des Viewports.
    *
    * Stand in allen drei Controls wortgleich in afterCompose -- ein ResizeObserver auf dem Viewport
    * plus ein resize-Listener am Fenster, beide auf scheduleViewportMeasure.
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

  /** Misst die Hoehe eines Header-Elements laufend und schreibt sie in `target`. Die drei Controls
    * unterscheiden sich nur darin, welches Element und welche Property sie uebergeben.
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

  /** Sichtbarer Bereich als `[start, end)`.
    *
    * Der vordere Zweig ist in allen drei Controls wortgleich: beim Server-Rendering und waehrend
    * der Hydration bestimmt der Crawl-Zustand den Ausschnitt, nicht die Scroll-Position -- sonst
    * waere der gerenderte Ausschnitt nicht reproduzierbar. Erst der else-Zweig unterscheidet sich,
    * und der liegt in der Geometrie.
    */
  protected def visibleRange(total: Int): (Int, Int) =
    if ((!browserRendering || hydrating) && crawlWindow.nonEmpty) {
      val (offset, limit) = crawlWindow.get
      val start           = math.min(offset, total)
      (start, math.min(total, start + limit))
    } else {
      geometry.visibleRange(total, scrollTopProperty.get, viewportHeightProperty.get)
    }

  /** Der beim Server-Rendering zu zeigende Ausschnitt, oder None. CrawlableCollection ueberschreibt
    * das.
    */
  protected def crawlWindow: Option[(Int, Int)] = None

  // --- Nachladen -----------------------------------------------------------

  /** Faehrt den Prefetch fuer den sichtbaren Bereich.
    *
    * Diese Fassung stammt aus DataGrid und VirtualListView; TableView hatte eine aeltere ohne
    * Prefetch-Fenster und ohne Seiten-Ausrichtung und erbt die Verbesserung hier mit. Siehe
    * VIRTUALIZATION.md.
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
        // Deduplizierung liegt in RemoteListProperty: gleichlautende Anfragen
        // teilen sich dort ein Future. Das Control muss darueber nicht Buch
        // fuehren.
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

  /** Ein Lade-Future, dessen Ergebnis das Control nicht braucht. Der recover verhindert eine
    * unbehandelte Fehlermeldung -- der Fehler selbst steht in RemoteListProperty.errorProperty und
    * wird von dort gerendert.
    */
  protected def discardResult(result: Future[?]): Unit = {
    result.recover { case _ => () }(using scala.concurrent.ExecutionContext.global)
    ()
  }
}
