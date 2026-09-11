# @anjunar/scalajs-ui-controls

Typed Scala JS UI 1.0 controls for tabs, carousels, and virtualized table, data-grid, and list views.

## Overview

This package wraps `ui.control` components. Virtualization, row measurement, sorting, remote range loading, carousel timers, and SSR/hydration behavior are implemented by the Scala.js runtime, not by a second TypeScript control library.

## Installation

```bash
npm install @anjunar/scalajs-ui-core @anjunar/scalajs-ui-controls @anjunar/scalajs-ui-bridge @anjunar/scalajs-ui
```

## Quick start

```ts
import { listProperty, text } from "@anjunar/scalajs-ui-core";
import { column, tableView } from "@anjunar/scalajs-ui-controls";

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

### Column widths and resizing

Columns accept `minWidth` (default 40), `prefWidth` (160), `maxWidth` (unbounded),
and reactive `resizable` (true). Drag the grip at the right edge of a header, or focus it and
press Left/Right (10 px; Shift: 1 px). Resizing does not sort the column or rebuild its cells.
Pointer-up/cancel, lost capture, window blur, hiding/removing the column, locking it, changing
the policy and unmount all end the gesture. Cancellation retains the last applied width.

```ts
const policy = property<ColumnResizePolicy>("flex-last-column");
const table = tableView(books, [
  valueColumn("Title", book => book.title, { minWidth: 140, prefWidth: 280, maxWidth: 900 }),
  valueColumn("Year", book => book.year, { minWidth: 70, prefWidth: 100, maxWidth: 300 }),
], { columnResizePolicy: policy });
table.resizeColumn(0, 40); // Visible-column index, pixel delta; true if any change was applied.
const widths: readonly number[] = table.columnWidths.get;
```

`ColumnResizePolicy` is exported by `@anjunar/scalajs-ui-controls`. Available policies:

| Policy | Compensation for a resized column |
| --- | --- |
| `unconstrained` | None; horizontal scrolling works in both paging and scrolling modes. |
| `all-columns` | Proportionally across all other resizable columns. |
| `subsequent-columns` | Proportionally across following resizable columns. |
| `next-column` / `last-column` | Only the next / last following column. |
| `flex-next-column` / `flex-last-column` | Following columns in forward / reverse order, continuing at limits. |

Constrained policies fit the viewport and suppress horizontal scrolling; impossible bounds
leave unused space or clipped columns. Locked columns do not participate in compensation.
The default is `flex-last-column`, preserving UI's existing fit-to-width layout. Automatic
viewport fitting uses bounded proportional distribution; policies govern user/API deltas.
Column groups and custom policy callbacks are not implemented yet.

User widths are separate from `prefWidth` and survive measurements and hide/show. A Scala
preferred-width change resets that column's user override; detaching it discards its override.
Scala exposes the same model via `minWidthProperty`, `maxWidthProperty`, `resizableProperty`,
read-only `widthProperty`, `columnResizePolicyProperty` and `resizeColumn(column, delta)`.
Hidden/detached columns report bounded preferred width through `widthProperty`; the table's
width snapshot only includes visible columns. Defaults remain UI-specific (40/160 px).
Negative minima normalize to zero, invalid non-finite minima/preferences use their defaults,
non-finite maxima are unbounded, and a conflicting minimum takes precedence over the maximum.
Invalid resize deltas/indices, hidden/foreign columns and disposed tables are no-ops.

Double-click a resize grip, or press Enter on its focus, to fit the column's content.
The same command is `table.autoFitColumn(visibleColumnIndex)` in TypeScript and
`table.autoFitColumn(column)` in Scala. It measures the header (including current sort
decoration) and at most 100 mounted, loaded cells, ordered by absolute row index.
Overscan cells may participate; unloaded/off-window data is not fetched or rendered.
The maximum intrinsic CSS width is rounded up, then applied through the existing resize
policy and min/max bounds. Constrained policies may only fit partially, or not at all
when no compensating column has capacity. The command can also shrink a column.

Measurement uses temporary max-content sizing on the existing header/cell hosts and
restores their width constraints synchronously. There are no clones or renderer calls;
custom controls keep their DOM, bindings, focus and text selection. Content keeps its CSS
layout semantics (e.g. explicit child widths); this is not a text-only width estimate.
Call again after fonts/images load or content changes if another fit is desired.

Auto-fit is browser-only; during hydration the latest valid request waits for claiming.
Invalid/hidden/locked columns, unmeasurable layout, protected hosts and active native
composition are no-ops, not queued retries. A true result means a width changed or a
hydration-time request was accepted; SSR and disposed handles return false.

### Remote multi-column sorting

Click a sortable header to cycle ascending, descending, unsorted. Shift-click keeps other
terms and their priorities, appends a new term, or changes/removes that term in place.
Focused headers support Enter/Space with the same Shift modifier; resize grips and reorder
gestures retain their own commands. Arrows include the one-based priority. `aria-sort` is
on the primary header only; other sorted headers describe their direction and priority.

`table.toggleSort(visibleColumnIndex, additive?)` and `table.clearSort()` use the same
remote command path, resetting paging and scrolling to the start. Scala takes a column
instance instead of an index. Commands are inert on local sources, invalid/hidden/unsortable
columns, protected hosts, active IME composition, during SSR/hydration and after disposal.
Hiding or moving a sorted column does not alter the remote query. `sorting` (Scala:
`sortingProperty`) exposes the remote source's **requested** descriptors. A true command
result means a request was issued, not that its load succeeded; the existing loading/error
state and accepted-reset selection rules still apply. Transactional sort rollback and
custom sort-policy/events are not provided by this increment.

The control never sorts/filters cached rows. Supply `sortKey`/`sortable` and a remote
`sortQuery` handling all descriptors in order. The demos simulate that backend in their
loaders; production applications do it through their RemoteDataList/server query.

Set an entire order explicitly, without cycling headers or issuing intermediate requests:

```ts
table.setSortOrder([
  { columnIndex: 1, ascending: true },
  { columnIndex: 2, ascending: false },
]);
table.sort(); // Reissue the current requested order, e.g. retry after a load error.
```

`TableSort` is the exported TypeScript term type. Indices refer to **currently visible**
columns at call time; they resolve immediately to remote field keys, which survive later
reordering. Scala uses `Seq(TableSort(authorColumn), TableSort(yearColumn, false))` with
column identities instead of indices. Validation is atomic: foreign/hidden/disposed or
unsortable columns, missing/blank keys and duplicate keys reject the entire order without
changing sorting, selection, paging or scrolling. JavaScript inputs also require finite
integer indices and real boolean directions. An empty sequence requests unsorted data.

Setting the same order again or calling `sort()` asks the source to reload; the source may
deduplicate an identical in-flight request. `sort()` preserves the current descriptors,
including terms whose columns have since been hidden or keys supplied directly by the
source. Both commands obey the browser/hydration/lifetime guards above. They do not
promise successful completion or implement sort rollback. A failed request retains the
existing rows/selection/focus; only an accepted replacement invalidates selection/focus.

### Column visibility menu

Set `tableMenuButtonVisible: true` to show a Columns button above the header. The table
must be inside `viewport(...)` from `@anjunar/scalajs-ui-viewport` when this option is enabled.
The existing Viewport owns the overlay and anchor positioning; the table owns its lifetime.
No native popover or second overlay implementation is involved. Without the option,
tables still work without a viewport. `showHeader: false` also hides the menu button.

The menu lists all columns in their current order, including hidden columns. It stays open
when toggling a checkbox and remains usable after hiding every column. Existing selection,
sorting and user widths are preserved. Hiding a column disposes its cells, as with `visible`.
Arrow keys/Home/End navigate, Enter/Space toggle, Escape closes and returns focus. Tab closes
and continues navigation from the trigger. Outside pointer/focus, window blur, structural
column changes, hiding the menu/header and unmount close and clean up the overlay/listeners.

`tableMenuButtonVisible` and `columnMenuText` are reactive; the latter supplies the trigger
text and accessible menu label. Scala exposes corresponding properties and DSL setters.
SSR renders only the disabled trigger; hydration enables it without opening or taking focus.
The trigger is disabled if the table has no columns. Visibility changes from menu actions
respect active native composition and protected table hosts.

`visible` is a one-way input. To keep application-owned state in sync with menu actions,
use `onVisibilityChange`, called on changes but not for the initial value:

```ts
const visible = property(true);
// Inside viewport(() => ...):
tableView(books, [valueColumn("Title", book => book.title, {
  visible,
  onVisibilityChange: next => visible.set(next),
})], { tableMenuButtonVisible: true, columnMenuText: "Spalten" });
```

### Column reordering

Drag a header (not its resize grip) to an insertion marker. Alternatively, focus the header
and press Alt+Shift+Left/Right. `reorderable: false` (also reactive) disables these gestures,
but not `table.moveColumn(fromVisibleIndex, toVisibleIndex)` or Scala column-list changes.
Indices refer to the current visible order; the destination is the final index, not a boundary.
Hidden columns keep their relative order. Moves change the canonical column list, preserving
mounted cells, bindings, focus/text selection, row selection, sorting and width overrides.

`moveColumn` is browser-only; SSR keeps the declared order and hydration applies the latest
valid command after claiming. Invalid/no-op requests, disposed tables, protected hosts and
active IME composition return false. Scala uses `moveColumn(column, toVisibleIndex)`.
Native composition is not interrupted to perform a move; retry after composition ends.
Escape, pointer cancellation/capture loss, blur, hiding/locking/removing the dragged column
or disposal cancel a drag. Dropping outside the header cancels too; drag release never sorts.
Column groups, drag-edge autoscrolling and a visibility menu are not implemented yet.

### Observed table values

`column(text, cell)` still composes arbitrary row content, including embedded editors.
`valueColumn` adds a typed snapshot/property accessor and an optional renderer over the
observed cell value:

```ts
import { listProperty, property, text } from "@anjunar/scalajs-ui-core";
import { tableView, valueColumn } from "@anjunar/scalajs-ui-controls";

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
import { attr, style } from "@anjunar/scalajs-ui-core";

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
import { remoteSource, virtualList } from "@anjunar/scalajs-ui-controls";

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
Table selection, logical row focus, navigation and refresh are available through its handle;
data-grid/list selection handles are not projected by this facade yet.

### Row focus and keyboard navigation

`table.focusIndex(index)`, `focusNext()` and `focusPrevious()` update `focusedIndex` and
`focusedItem` independently of selection. These are logical model operations: they do not scroll,
fetch remote data or take DOM focus. Invalid indices clear focus (`-1`/null); unloaded known
positions have an index and null item until loaded. Previous at the beginning does nothing;
next from no focus chooses the first row. Operations are inert after disposal.

The grid is a native tab stop. On focus it restores logical focus, or starts at the selected row /
first row. A background row click focuses the grid and selects the row. Embedded inputs, buttons,
links, contenteditable and other focusable controls keep their own interaction; header, resize
and menu controls also retain their own keys. Logical focus survives leaving the grid, but the
row outline is shown only while the grid itself has DOM focus.

- Up/Down, Home/End, PageUp/PageDown move focus, select the destination and reveal it.
- Ctrl/Cmd with navigation moves focus without changing selection.
- Shift extends the anchored selection; Ctrl/Cmd+Shift adds the range to the selection.
- Space selects; Ctrl/Cmd+Space toggles. Ctrl/Cmd+A selects all in multiple-selection mode.
- Tab retains native navigation. Alt-modified, canceled and IME-composition keys are ignored.

Page movement uses the measured viewport height with one row of overlap (minimum one row).
Paging remains paging; remote targets load through normal row navigation. Structural list edits
rebase focused occurrences; removal clears focus. A reset preserves only unique reference identity;
an accepted remote query replacement clears focus. `row.focused` exposes logical row state to
custom renderers. Native focus stays on the grid as virtual rows enter/leave; `aria-activedescendant`
only references mounted rows, with browser IDs installed after hydration without stealing focus.
Grid/row/cell/header roles, row/column counts and indices accompany this first accessibility layer.
Cell focus/selection, replaceable focus models, sort announcements and full screenreader/IME
acceptance are still pending. Runtime-managed row IDs are reserved for active-descendant ownership.

`table.scrollToIndex(499)` reveals the 500th row in absolute view coordinates;
`table.scrollToItem(item)` reveals the first matching loaded item. Neither changes selection,
DOM focus or the display mode. In paging mode the containing page is shown; if that page is
taller than the viewport, its rows can also be revealed programmatically. In scrolling mode,
already visible rows stay in place. Content headers are included and the last row is clamped
to the end of the content.

Invalid/unknown indices and absent items are ignored. A known but unloaded remote position
uses the existing range loader (including its loading/error/retry behavior); item lookup never
fetches missing items. Sources without random range access retain their sequential loading
behavior. These methods do not discover unknown positions beyond the source's current extent.

Navigation is browser-only: SSR keeps its deterministic slice. Requests made during
composition/hydration or while the viewport has no height/visible columns wait until it can be
measured. The latest valid request wins, is revalidated against the current extent, and
supersedes the initial cookie/URL scroll restoration. Disposal cancels pending navigation.
There is no load-completion promise or `onScrollTo` event yet.

`table.scrollToColumnIndex(index)` reveals a column in the current **visible** order with minimal
horizontal movement. Oversized columns align at their start. Hidden columns contribute neither
indices nor width. The operation uses current rendered widths after resizing/reordering, leaves
vertical position, selection, focus and editors untouched, and does not load remote rows. It also
works with no rows or a hidden header. Widths and the resize policy are not changed: usually use
free (`unconstrained`) widths when horizontal scrolling is desired.

Column navigation is browser-only too. The last valid request waits for hydration or a non-zero
viewport width and remembers the column instance, not an index that could change during reordering.
The target is revalidated before scrolling; hidden/removed targets and disposed tables are ignored.
The header offset is synchronized immediately, including browser clamping. Row and column requests
are independent. Scala additionally accepts a column reference via `scrollToColumn(column)`;
`onScrollToColumn` events and RTL navigation remain pending.

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

- [`@anjunar/scalajs-ui-core`](../scalajs-ui-core/README.md) provides state and render callbacks.
- [`@anjunar/scalajs-ui-viewport`](../scalajs-ui-viewport/README.md) provides the global UI layer.
