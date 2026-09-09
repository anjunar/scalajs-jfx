# @anjunar/jfx-controls

Typed JFX 3 controls for tabs, carousels, and virtualized table, data-grid, and list views.

## Overview

This package wraps `jfx.control` components. Virtualization, row measurement, sorting, remote range loading, carousel timers, and SSR/hydration behavior are implemented by the Scala.js runtime, not by a second TypeScript control library.

## Installation

```bash
npm install @anjunar/jfx-core @anjunar/jfx-controls @anjunar/scalajs-jfx-bridge @anjunar/scalajs-jfx
```

## Quick start

```ts
import { listProperty, text } from "@anjunar/jfx-core";
import { column, tableView } from "@anjunar/jfx-controls";

type Book = { title: string; year: number };
const books = listProperty<Book>([
  { title: "A", year: 2024 },
  { title: "B", year: 2025 },
]);

tableView(books, [
  column("Title", (book) => text(book.title), { prefWidth: 280, sortable: true, sortKey: "title" }),
  column("Year", (book) => text(String(book.year))),
], { rowHeight: 44 });
```

## Usage

### Observed table values

`column(text, cell)` still composes arbitrary row content, including embedded editors.
`valueColumn` adds a typed snapshot/property accessor and an optional renderer over the
observed cell value:

```ts
import { listProperty, property, text } from "@anjunar/jfx-core";
import { tableView, valueColumn } from "@anjunar/jfx-controls";

const person = { name: property("Ada"), year: 1815 };
tableView(listProperty([person]), [
  valueColumn("Name", (row) => row.name),
  valueColumn("Greeting", (row) => row.name, {
    cell: (value) => text(value.map((name) => `Hello ${name ?? ""}`)),
  }),
  valueColumn("Year", (row) => row.year),
]);
person.name.set("Grace"); // Updates both observed cells without recomposing them.
```

Snapshots do not observe mutations. Custom value renderers receive a read-only property;
editing still requires the row's writable property or an explicit application callback.
Cells at overlapping absolute row positions retain their component/DOM identity during
scrolling and measurement when the item instance is unchanged. Leaving the virtual window,
replacing an item, resetting the source list, or changing the renderer can recreate cells.
Identity across data reordering and table-managed start/commit/cancel are not implemented yet.

### Column visibility and refresh

Column `visible` accepts a boolean or a `ReadOnlyProperty<boolean>` (default: true).
Hidden columns do not render headers/cells or consume width. Their definitions remain
attached; showing them again creates fresh cells. Other visible cells remain mounted when
a neighboring column is hidden or shown. With no visible columns, the table displays its
placeholder instead of rows. This currently supports flat columns, not grouped headers.

```ts
const showYear = property(true);
const book = { title: "Dune", year: 1965 };
const table = tableView(listProperty([book]), [
  valueColumn("Title", (row) => row.title),
  valueColumn("Year", (row) => row.year, { visible: showYear }),
]);
showYear.set(false);
book.title = "Solaris";
table.refresh(); // Re-evaluates visible snapshots and cell bodies; no remote reload.
```

`tableView()` now returns `TableViewHandle` with `refresh()` and read-only `isDisposed`.
After unmount, refresh is a no-op. Refresh recreates visible cell content and can reset local
editor state, so use observed values for live edits. Calling code may ignore the new return
value. An explicitly void-returning expression arrow should use a block:
`() : void => { tableView(source, columns); }`. The matching linked bridge must be installed.

### Other controls

`tabs` accepts `tab(title, body)` definitions and supports `active-only` or `keep-mounted` rendering. `carousel` accepts a list property and a slide renderer; `autoAdvanceMs` controls browser auto-advance. `tableView`, `dataGrid`, and `virtualList` accept a local `ListProperty` or a `RemoteSource`.

```ts
import { remoteSource, virtualList } from "@anjunar/jfx-controls";

type Item = { title: string };
type Query = { offset: number; limit: number };
const firstPage: readonly Item[] = [{ title: "First item" }];

const source = remoteSource<Item, Query>({
  initialQuery: { offset: 0, limit: 50 },
  initial: firstPage,
  initialOffset: 0,
  totalCount: 1000,
  rangeQuery: (query, offset, limit) => ({ ...query, offset, limit }),
  load: async (query) => ({ items: firstPage, offset: query.offset, totalCount: 1000 }),
});

virtualList(source, (item, index) => text(item === null ? `Loading ${index}` : item.title));
```

`crawlable` and `crawlId` render a deterministic server slice with ordinary pager links. Imperative control handles such as selection and scrolling are not projected by this facade.

Paged or scrolling content headers can declare their reserved height with `headerRows`. For
`dataGrid`, one row is one card height and the header always spans the full responsive grid width;
for `tableView` and `virtualList`, rows use the table row height and estimated item height
respectively. If `headerRows` is omitted, the browser continues to measure the rendered header.

## SSR and non-JavaScript behavior

SSR renders a stable paged or crawl slice. After successful hydration, `tableView`, `dataGrid`, and `virtualList` automatically switch the default fallback to scrolling and retain the server-rendered offset. Set `paging: true` to keep paging in the browser. A crawler cannot scroll; use crawlability or paging when deeper collection content must be addressable without JavaScript.

## API overview

- `tab`, `tabs`, `carousel`
- `column`, `valueColumn`, `ValueColumnOptions`, `tableView`, `dataGrid`, `virtualList`
- `remoteSource`, `RemoteSource`, `RemotePage`, `SortSpec`
- `TabsOptions`, `CarouselOptions`, `TableViewOptions`, `TableViewHandle`, `DataGridOptions`, `VirtualListOptions`

## Related modules

- [`@anjunar/jfx-core`](../jfx-core/README.md) provides state and render callbacks.
- [`@anjunar/jfx-viewport`](../jfx-viewport/README.md) provides the global UI layer.
