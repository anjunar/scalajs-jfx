# scalajs-jfx-controls

Higher-level JFX3 controls for tabs, carousels, and virtualized collections: `TableView`, `DataGrid`, and `VirtualListView`.

## Overview

The controls build on `jfx-core` state and data-source contracts. They render only the visible portion of large collections, measure their viewport, and can request missing ranges from a remote source. Crawlable collections also render a deterministic server-side slice and pager links for clients that cannot scroll.

## Installation

```scala
libraryDependencies += "com.anjunar" %% "scalajs-jfx-controls" % "3.0.0"
```

## Quick start

```scala
import jfx.control.table.TableColumn.column
import jfx.control.table.TableView.tableView
import jfx.core.layout.TextComponent.text
import jfx.core.state.ListProperty

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

The three collection controls accept local `ListProperty` values or remote list data sources from `jfx.core.remote`. Remote sources can load more data, load ranges, expose total counts, and carry sorting state. Their shared geometry and loading model is described by the `VirtualizedCollection` and `CrawlableCollection` abstractions in the module source.

## SSR and non-JavaScript behavior

SSR renders a stable slice of a collection. With crawlability enabled, the slice is addressable through ordinary pager links and a crawl cookie stores the visitor's position. JavaScript adds viewport measurement, scrolling, range loading, sorting interaction, and efficient replacement of the visible window. A crawler cannot scroll, so do not rely on scrolling alone to expose important content.

## API overview

- `jfx.control.tabs.Tabs` and `TabPanel`
- `jfx.control.carousel.Carousel`
- `jfx.control.table.TableView` and `TableColumn`
- `jfx.control.datagrid.DataGrid`
- `jfx.control.virtuallist.VirtualListView`
- `jfx.core.remote.RemoteListProperty`, `RemoteListDataSource`, and `RemoteSort`

## Related modules

- [`jfx-core`](../jfx-core/README.md) provides `Property`, `ListProperty`, and rendering.
- [`jfx-viewport`](../jfx-viewport/README.md) is used by higher-level controls that need floating UI.
- [`jfx-forms`](../jfx-forms/README.md) builds model-bound controls on top of the same state primitives.
