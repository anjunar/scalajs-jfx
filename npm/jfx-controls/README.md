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

`tableView()` returns `TableViewHandle<T>` with `refresh()` and read-only `isDisposed`,
plus the single-selection API below.
After unmount, refresh is a no-op. Refresh recreates visible cell content and can reset local
editor state, so use observed values for live edits. Calling code may ignore the new return
value. An explicitly void-returning expression arrow should use a block:
`() : void => { tableView(source, columns); }`. The matching linked bridge must be installed.

### Single selection

The handle exposes read-only `selectedIndex: ReadOnlyProperty<number>` and
`selectedItem: ReadOnlyProperty<T | null>`. Both read from one coherent state:

```ts
table.selectIndex(0);
text(table.selectedItem.map((selected) => selected?.title ?? "No selection"));
table.selectItem(book); // First equal loaded item; does not fetch missing rows.
table.clearSelection(); // selectedIndex = -1, selectedItem = null.
```

Local inserts/removals move the selection with its occurrence, including duplicates.
Removing or replacing that occurrence clears it; an explicit item update retains the
index and adopts the updated item. A list reset retains selection only when the same
object instance occurs exactly once in the new list, not merely an equal new object.

Remote indices are absolute. Loading a gap does not shift selection. Selecting a valid
unloaded position yields a null item until it loads. Accepted reload/sort replacements
clear selection; an in-flight or failed replacement leaves the previous selection intact.
Invalid indices (including fractions, NaN, and infinity) clear selection. `selectItem`
may scan the entire index space, so prefer a known index for large remote sources.

After unmount, selection operations are no-ops. Reactive `text` bindings follow normal
component lifecycle; dispose subscriptions that you create manually with `observe`.
Derived properties may notify even when only the other selection field changes.
Cell selection and a separate focus model are not available yet; multi-selection is below.
Selection identity does not guarantee DOM/editor identity across data reordering.

### Multiple selection

Pass `selectionMode: "multiple"` (or a reactive property) to `tableView`. The default
remains `"single"`. The same runtime-owned handle exposes both modes:

```ts
const table = tableView(books, [valueColumn("Title", (book) => book.title)], {
  selectionMode: "multiple",
});
table.selectIndices([0, 2]);
table.selectRange(3, 6); // Adds 3, 4, 5; reversed ranges also exclude the end.
text(table.selectedIndices.map((indices) => `${indices.length} selected`));
table.clearIndex(2);
table.clearAndSelect(0); // Replace the entire selection in one update.
```

`selectedIndices` is a read-only property of sorted, unique absolute indices;
`selectedItems` contains their loaded items in index order. `selectedIndex`/`selectedItem`
describe the **lead** (last valid selection), not the whole result. Removing the lead
chooses the largest remaining selected index. All properties read from one coherent
snapshot. Returned arrays cannot mutate the model (item objects remain application-owned).

In multiple mode, `selectIndex`/`selectItem` add to selection. `selectIndices` ignores invalid
and duplicate values; ranges clip to valid positions. `clearIndex` ignores invalid positions.
Non-integral/NaN/infinite range boundaries are ignored. Invalid `selectIndex` and
`clearAndSelect` still clear selection for compatibility with the original single API.
`selectAll` works only in multiple mode. `selectFirst`, `selectLast`, `selectNext` and
`selectPrevious` operate on the lead and do not move DOM focus or scroll the viewport.

Plain click replaces selection. Ctrl/Cmd-click toggles a row; Shift-click replaces it with
the inclusive anchor-to-click range; Ctrl/Cmd+Shift adds that range. Repeated Shift-clicks
keep the anchor, including after index rebasing. Loaded custom rows use the same membership
state. Switching to single via `setSelectionMode("single")` retains just the lead.
The reactive option is one-way: handle calls do not write back to the supplied property.

For sparse remote data, selected indices may outnumber selected items: **do not zip the
two lists**. Selection never fetches missing values. `selectAll` explicitly selects the
currently known index space and uses memory proportional to its size; it is not a
server-side all-results token and does not expand automatically when the source grows.
Accepted reloads clear the selection; structural deltas preserve surviving occurrences.
Keyboard range navigation, a replaceable selection model, cell selection and full grid
accessibility remain separate work.

### Custom rows

`TableViewOptions<T>.row` replaces the content of each row. Its callback runs in that
row's component scope: styles, attributes, events and `disposeWith` belong to the row.
Call `renderCells()` to include the usual columns, or omit it for entirely custom content:

```ts
import { attr, style } from "@anjunar/jfx-core";

tableView(books, [valueColumn("Title", (book) => book.title)], {
  row: (row) => {
    if (!row.empty.get) attr("title", row.item.get?.title ?? "");
    style("font-weight", row.selected.map((selected) => selected ? "600" : "400"));
    row.renderCells();
  },
});
```

`TableRowContext<T>` exposes read-only `item`, absolute `index`, `empty`, and `selected`
properties. Remote placeholders also receive the callback, with `empty = true` and
`item = null`; they remain non-interactive and unselected until replaced by loaded rows.
A loaded null value is distinguishable by `empty = false`. Row selection, standard classes
and cleanup remain runtime-owned even when standard cells are omitted.

`renderCells()` may be called at most once, synchronously in the row callback or inside
a nested element built by that callback. Give custom wrappers the appropriate layout
(for example `display: flex`) to retain column alignment. Deferred calls are rejected.
The callback does not rerun on selection changes: use the supplied reactive properties.
It does run for newly materialized rows and on `refresh()`. Refresh now recreates whole
visible rows, including custom snapshot content; it may discard local editor state.
Overlapping scroll slots with unchanged items retain their row instances. Fixed row
height still applies; this is not automatic cell spanning or a variable-height layout.

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

`crawlable` and `crawlId` render a deterministic server slice with ordinary pager links.
Table selection and refresh are available through its handle; imperative scrolling and
data-grid/list selection handles are not projected by this facade yet.

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
- `TabsOptions`, `CarouselOptions`, `TableViewOptions<T>`, `TableViewHandle<T>`, `TableRowContext<T>`, `DataGridOptions`, `VirtualListOptions`

## Related modules

- [`@anjunar/jfx-core`](../jfx-core/README.md) provides state and render callbacks.
- [`@anjunar/jfx-viewport`](../jfx-viewport/README.md) provides the global UI layer.
