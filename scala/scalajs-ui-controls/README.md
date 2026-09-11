# scalajs-ui-controls

Higher-level Scala JS UI 1.0 controls for tabs, carousels, and virtualized collections: `TableView`, `DataGrid`, and `VirtualListView`.

## Overview

The controls build on `scalajs-ui-core` state and data-source contracts. They render only the visible portion of large collections, measure their viewport, and can request missing ranges from a remote source. Crawlable collections also render a deterministic server-side slice and pager links for clients that cannot scroll.

## Installation

```scala
libraryDependencies += "com.anjunar" %% "scalajs-ui-controls" % "1.0.0"
```

## Quick start

```scala
import ui.control.table.TableColumn.column
import ui.control.table.TableView.tableView
import ui.core.layout.TextComponent.text
import ui.core.state.ListProperty

final case class Book(title: String, year: Int)
val books = ListProperty[Book]()
books += Book("A", 2024)
books += Book("B", 2025)

tableView(books) {
  column("Title") { book => text(book.title) {} }
  column("Year") { book => text(book.year.toString) {} }
}
```

## Usage

- `Tabs` mounts one selected panel by default. Its keep-mounted mode retains inactive panel components.
- `Carousel` renders slides from a `ListProperty` and can auto-advance in the browser.
- `TableView` renders rows and columns, including sortable headers.
- `DataGrid` renders a two-dimensional cell layout.
- `VirtualListView` renders one variable-height item per row.

The three collection controls accept local `ListProperty` values or remote list data sources from `ui.core.remote`. Remote sources can load more data, load ranges, expose total counts, and carry sorting state. Their shared geometry and loading model is described by the `VirtualizedCollection` and `CrawlableCollection` abstractions in the module source.

## SSR and non-JavaScript behavior

SSR renders a stable paged slice of a collection. With crawlability enabled, the slice is addressable through ordinary pager links and a crawl cookie stores the visitor's position. After successful hydration, `TableView`, `DataGrid`, and `VirtualListView` automatically switch that default fallback to scrolling and retain the server-rendered offset. Setting `paging = true` keeps paging in the browser; an explicit `scrolling = true` also remains available. A crawler cannot scroll, so do not rely on scrolling alone to expose important content.

## API overview

- `ui.control.tabs.Tabs` and `TabPanel`
- `ui.control.carousel.Carousel`
- `ui.control.table.TableView` and `TableColumn`
- `ui.control.datagrid.DataGrid`
- `ui.control.virtuallist.VirtualListView`
- `ui.core.remote.RemoteListProperty`, `RemoteListDataSource`, and `RemoteSort`

## Related modules

- [`scalajs-ui-core`](../scalajs-ui-core/README.md) provides `Property`, `ListProperty`, and rendering.
- [`scalajs-ui-viewport`](../scalajs-ui-viewport/README.md) is used by higher-level controls that need floating UI.
- [`scalajs-ui-forms`](../scalajs-ui-forms/README.md) builds model-bound controls on top of the same state primitives.
