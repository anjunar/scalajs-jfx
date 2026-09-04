package jfx.bridge

import jfx.control.carousel.Carousel
import jfx.control.datagrid.DataGrid
import jfx.control.table.{TableColumn, TableView}
import jfx.control.tabs.Tabs
import jfx.control.virtuallist.VirtualListView
import jfx.core.component.AbstractComponent
import jfx.core.remote.{RemoteListProperty, RemoteLoader, RemotePage, RemoteSort}
import jfx.core.render.Cursor
import jfx.core.state.{
  ListDataSource,
  ListProperty => CoreListProperty,
  ReadOnlyProperty => CoreReadOnlyProperty
}

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

/** Step 6 of JAVASCRIPT_API.md §9: the controls facade.
  *
  * The trigger from CLAUDE_REVIEW_3.md §5 was "`jfx-bridge` exports the controls registry --
  * `table-view`, `data-grid`, `tabs`, `carousel`, `virtual-list-view`". This file is those five
  * factories plus the JS <-> Scala translation they need: a data source (local `ListProperty` or a
  * remote spec), item renderers, and the table column model.
  *
  * The item type is `js.Any` end to end -- a renderer or a cell hands back the exact opaque object
  * the JS consumer put into the source. Nothing about the item is interpreted on this side.
  *
  * What is *not* projected in this pass (each has a trigger in CLAUDE_REVIEW_3.md, Nachtrag Lauf 4):
  * imperative handles (`carousel.next()`, `tableView.select(item)`, `dataGrid.scrollTo`), the
  * `onRowDoubleClick` / `selectedItem` readback, and `TableColumn.cellValueFactory` (which already
  * throws in Scala). The facade is reactive-input only: reactive options in, no handle out.
  */

@js.native
private[bridge] trait RemoteSourceFacade extends js.Object {

  /** Loads one page for a query. The query shape is owned entirely by the JS consumer; this side
    * only carries it back and forth as an opaque value.
    */
  val load: js.Function1[js.Any, js.Promise[RemotePageFacade]] = js.native

  val initialQuery: js.Any = js.native

  /** The first page, already materialized. Server rendering shows exactly this slice -- there is no
    * synchronous mount point at which the bridge could await `load`.
    */
  val initial: js.UndefOr[js.Array[js.Any]] = js.native

  val totalCount: js.UndefOr[Int] = js.native

  /** `(query, offset, limit) => query` -- enables range loading while scrolling. */
  val rangeQuery: js.UndefOr[js.Function3[js.Any, Int, Int, js.Any]] = js.native

  /** `(query, sorting) => query` -- enables the sortable column header. */
  val sortQuery: js.UndefOr[js.Function2[js.Any, js.Array[SortFacade], js.Any]] = js.native
}

@js.native
private[bridge] trait RemotePageFacade extends js.Object {
  val items: js.Array[js.Any]         = js.native
  val offset: js.UndefOr[Int]         = js.native
  val totalCount: js.UndefOr[Int]     = js.native
  val nextQuery: js.UndefOr[js.Any]   = js.native
  val hasMore: js.UndefOr[Boolean]    = js.native
}

/** Mirrors `jfx.core.remote.RemoteSort`. */
@js.native
private[bridge] trait SortFacade extends js.Object {
  val field: String      = js.native
  val ascending: Boolean = js.native
}

@js.native
private[bridge] trait TabFacade extends js.Object {
  val title: js.Any                                   = js.native
  val content: js.Function1[ScopeHandleBridge, Unit]  = js.native
}

@js.native
private[bridge] trait ColumnFacade extends js.Object {
  val text: String                 = js.native
  val prefWidth: js.UndefOr[Double] = js.native
  val sortable: js.UndefOr[Boolean] = js.native
  val sortKey: js.UndefOr[String]   = js.native

  /** `(row) => (scope) => void` -- the cell body, already wrapped in `withScope` on the TS side. */
  val cell: js.Function1[js.Any, js.Function1[ScopeHandleBridge, Unit]] = js.native
}

private[bridge] object ControlFactories {

  /** A local `ListProperty` (already a `ListDataSource`) or a remote spec. */
  def source(value: js.Any)(using ExecutionContext): ListDataSource[js.Any] =
    value match {
      case handle: ListPropertyHandle[?] =>
        handle.underlyingList.asInstanceOf[CoreListProperty[js.Any]]
      case _ =>
        remoteSource(value.asInstanceOf[RemoteSourceFacade])
    }

  private def remoteSource(
      facade: RemoteSourceFacade
  )(using ExecutionContext): RemoteListProperty[js.Any, js.Any] = {
    val loader = RemoteLoader[js.Any, js.Any] { query =>
      facade.load(query).toFuture.map { page =>
        RemotePage[js.Any, js.Any](
          items = page.items.toSeq,
          offset = page.offset.toOption,
          nextQuery = page.nextQuery.toOption,
          totalCount = page.totalCount.toOption,
          hasMore = page.hasMore.toOption
        )
      }
    }

    val remote = new RemoteListProperty[js.Any, js.Any](
      loader = loader,
      initialQuery = facade.initialQuery,
      underlying = facade.initial.getOrElse(js.Array[js.Any]()),
      sortUpdater = facade.sortQuery.toOption.map { fn => (query: js.Any, sorting: Seq[RemoteSort]) =>
        fn(query, sorting.map(sortFacade).toJSArray)
      },
      rangeQueryUpdater = facade.rangeQuery.toOption.map { fn => (query: js.Any, offset: Int, limit: Int) =>
        fn(query, offset, limit)
      }
    )

    facade.totalCount.toOption.foreach(count => remote.totalCountProperty.set(Some(count)))
    remote
  }

  private def sortFacade(sort: RemoteSort): SortFacade =
    js.Dynamic.literal(field = sort.field, ascending = sort.ascending).asInstanceOf[SortFacade]

  /** A `(scope) => void` from TS, run against a fresh handle built from the ambient context. Mirrors
    * `RouterFactories.routeComponent`.
    */
  def slotBody(
      fn: js.Function1[ScopeHandleBridge, Unit]
  ): AbstractComponent ?=> Cursor ?=> Unit =
    (_: AbstractComponent) ?=>
      (_: Cursor) ?=>
        fn(new ScopeHandleBridge(summon[AbstractComponent], summon[Cursor]))

  /** A `(item, index) => (scope) => void` from TS, as a control cell renderer. */
  def itemRenderer(
      fn: js.Function2[js.Any, Int, js.Function1[ScopeHandleBridge, Unit]]
  ): (js.Any | Null, Int) => AbstractComponent ?=> Cursor ?=> Unit =
    (item, index) =>
      (_: AbstractComponent) ?=>
        (_: Cursor) ?=>
          fn(item.asInstanceOf[js.Any], index)(
            new ScopeHandleBridge(summon[AbstractComponent], summon[Cursor])
          )

  // --- option readers -------------------------------------------------------

  private[bridge] def dbl(value: js.Any): Double  = value.asInstanceOf[Double]
  private[bridge] def int(value: js.Any): Int     = value.asInstanceOf[Double].toInt
  private[bridge] def bool(value: js.Any): Boolean = value.asInstanceOf[Boolean]
  private[bridge] def str(value: js.Any): String  = value.asInstanceOf[String]

  /** Constant or reactive, always resolved to a property -- a constant becomes a `ConstantProperty`
    * that the control observes once.
    */
  private[bridge] def intProp(value: js.Any): CoreReadOnlyProperty[Int] =
    ReactiveBridge.asProperty[Double](value).map(_.toInt)

  private[bridge] def boolProp(value: js.Any): CoreReadOnlyProperty[Boolean] =
    ReactiveBridge.asProperty[Boolean](value)

  private[bridge] def strProp(value: js.Any): CoreReadOnlyProperty[String] =
    ReactiveBridge.asProperty[String](value)
}

/** `tabs` -- a tab strip declared from an array of `{ title, content }`. */
private[bridge] object TabsFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent = {
    val tabDefs = options("tabs").asInstanceOf[js.Array[TabFacade]]

    Tabs.tabs {
      options.get("renderMode").foreach { mode =>
        Tabs.renderMode =
          ControlFactories.str(mode) match {
            case "keep-mounted" => Tabs.RenderMode.KeepMountedHidden
            case _              => Tabs.RenderMode.ActiveOnly
          }
      }

      // Tabs are registered before the selection is set: `setSelectedIndex` clamps against the
      // current tab count, so a selection applied to an empty strip would collapse to 0.
      tabDefs.foreach { tab =>
        Tabs.tab(ControlFactories.strProp(tab.title)) {
          tab.content(new ScopeHandleBridge(summon[AbstractComponent], summon[Cursor]))
        }
      }

      options.get("selectedIndex").foreach(value => Tabs.selectedIndex_=(ControlFactories.intProp(value)))
    }
  }
}

/** `carousel` -- looping slides over a `ListProperty`, one renderer per slide. */
private[bridge] object CarouselFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent = {
    val items = options("items")
      .asInstanceOf[ListPropertyHandle[js.Any]]
      .underlyingList
    val slide = options("slideRenderer")
      .asInstanceOf[js.Function2[js.Any, Int, js.Function1[ScopeHandleBridge, Unit]]]

    Carousel.carousel[js.Any] {
      val self: Carousel[js.Any] = summon[Carousel[js.Any]]
      self.setItems(items)
      self.setRenderer { (item: js.Any, index: Int) =>
        (_: AbstractComponent) ?=>
          (_: Cursor) ?=>
            slide(item, index)(new ScopeHandleBridge(summon[AbstractComponent], summon[Cursor]))
      }
      options.get("autoAdvanceMs").foreach(value => Carousel.autoAdvanceMs_=(ControlFactories.intProp(value)))
      options.get("wrapAround").foreach(value => Carousel.wrapAround_=(ControlFactories.boolProp(value)))
      options.get("ssrShowAllStates").foreach(value =>
        Carousel.ssrShowAllStates_=(ControlFactories.boolProp(value))
      )
      options.get("activeIndex").foreach(value => Carousel.activeIndex_=(ControlFactories.intProp(value)))
    }
  }
}

/** `table-view` -- fixed-height rows, a column model, an optional scrolling content header. */
private[bridge] object TableViewFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent = {
    given ExecutionContext = ExecutionContext.global

    val src     = ControlFactories.source(options("source"))
    val columns = options("columns").asInstanceOf[js.Array[ColumnFacade]]

    TableView.tableView[js.Any](src) {
      options.get("rowHeight").foreach(value => TableView.rowHeight = ControlFactories.dbl(value))
      options.get("showHeader").foreach(value => TableView.showHeader = ControlFactories.bool(value))
      options.get("showFooter").foreach(value => TableView.showFooter = ControlFactories.bool(value))
      options.get("paging").foreach(value => TableView.paging = ControlFactories.bool(value))
      options.get("pageSize").foreach(value => TableView.pageSize = ControlFactories.int(value))
      options.get("crawlable").foreach(value => TableView.crawlable = ControlFactories.bool(value))
      options.get("crawlId").foreach(value => TableView.crawlId = ControlFactories.str(value))

      columns.foreach { col =>
        TableColumn.column[js.Any, js.Any](col.text) {
          col.prefWidth.foreach(width => TableColumn.prefWidth_=[js.Any, js.Any](width))
          col.sortable.foreach(flag => TableColumn.sortable_=[js.Any, js.Any](flag))
          col.sortKey.foreach(key => TableColumn.sortKey_=[js.Any, js.Any](key))
          TableColumn.cell[js.Any, js.Any] { (row: js.Any) =>
            (_: AbstractComponent) ?=>
              (_: Cursor) ?=>
                col.cell(row)(new ScopeHandleBridge(summon[AbstractComponent], summon[Cursor]))
          }
        }
      }

      options.get("header").foreach { slot =>
        TableView.header[js.Any](
          ControlFactories.slotBody(slot.asInstanceOf[js.Function1[ScopeHandleBridge, Unit]])
        )
      }
      options.get("placeholder").foreach { slot =>
        TableView.placeholder[js.Any](
          ControlFactories.slotBody(slot.asInstanceOf[js.Function1[ScopeHandleBridge, Unit]])
        )
      }
    }
  }
}

/** `data-grid` -- fixed-size cells in a responsive column count, one renderer per cell. */
private[bridge] object DataGridFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent = {
    given ExecutionContext = ExecutionContext.global

    val src  = ControlFactories.source(options("source"))
    val cell = options("cellRenderer")
      .asInstanceOf[js.Function2[js.Any, Int, js.Function1[ScopeHandleBridge, Unit]]]

    DataGrid.dataGrid[js.Any](src) {
      DataGrid.cellRenderer = ControlFactories.itemRenderer(cell)
      options.get("itemWidthPx").foreach(value => DataGrid.itemWidthPx = ControlFactories.dbl(value))
      options.get("itemHeightPx").foreach(value => DataGrid.itemHeightPx = ControlFactories.dbl(value))
      options.get("gapPx").foreach(value => DataGrid.gapPx = ControlFactories.dbl(value))
      options.get("overscanRows").foreach(value => DataGrid.overscanRows = ControlFactories.int(value))
      options.get("prefetchItems").foreach(value => DataGrid.prefetchItems = ControlFactories.int(value))
      options.get("paging").foreach(value => DataGrid.paging = ControlFactories.bool(value))
      options.get("pageSize").foreach(value => DataGrid.pageSize = ControlFactories.int(value))
      options.get("crawlable").foreach(value => DataGrid.crawlable = ControlFactories.bool(value))
      options.get("crawlId").foreach(value => DataGrid.crawlId = ControlFactories.str(value))

      options.get("header").foreach { slot =>
        DataGrid.header[js.Any](
          ControlFactories.slotBody(slot.asInstanceOf[js.Function1[ScopeHandleBridge, Unit]])
        )
      }
      options.get("loadingPlaceholder").foreach { slot =>
        DataGrid.loadingPlaceholder[js.Any](
          ControlFactories.slotBody(slot.asInstanceOf[js.Function1[ScopeHandleBridge, Unit]])
        )
      }
      options.get("emptyPlaceholder").foreach { slot =>
        DataGrid.emptyPlaceholder[js.Any](
          ControlFactories.slotBody(slot.asInstanceOf[js.Function1[ScopeHandleBridge, Unit]])
        )
      }
    }
  }
}

/** `virtual-list-view` -- measured row heights, one renderer per row. */
private[bridge] object VirtualListFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent = {
    given ExecutionContext = ExecutionContext.global

    val src  = ControlFactories.source(options("source"))
    val cell = options("cellRenderer")
      .asInstanceOf[js.Function2[js.Any, Int, js.Function1[ScopeHandleBridge, Unit]]]

    VirtualListView.virtualList[js.Any](src) {
      VirtualListView.cellRenderer = ControlFactories.itemRenderer(cell)
      options.get("estimateHeightPx").foreach(value =>
        VirtualListView.estimateHeightPx = ControlFactories.dbl(value)
      )
      options.get("overscanPx").foreach(value => VirtualListView.overscanPx = ControlFactories.dbl(value))
      options.get("prefetchItems").foreach(value =>
        VirtualListView.prefetchItems = ControlFactories.int(value)
      )
      options.get("paging").foreach(value => VirtualListView.paging = ControlFactories.bool(value))
      options.get("pageSize").foreach(value => VirtualListView.pageSize = ControlFactories.int(value))
      options.get("crawlable").foreach(value => VirtualListView.crawlable = ControlFactories.bool(value))
      options.get("crawlId").foreach(value => VirtualListView.crawlId = ControlFactories.str(value))

      options.get("header").foreach { slot =>
        VirtualListView.header[js.Any](
          ControlFactories.slotBody(slot.asInstanceOf[js.Function1[ScopeHandleBridge, Unit]])
        )
      }
    }
  }
}
