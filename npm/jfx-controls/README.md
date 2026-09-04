# @anjunar/jfx-controls

The controls API of JFX3 in TypeScript: a tab strip, a looping carousel, and the
three virtualized collections -- table, data grid, list.

Like every package in the family, this is **types and ergonomics, not a
framework**. Virtualization, row measurement, the sortable header, range loading,
the auto-advance timer, panel lifecycle and the stable SSR / hydration structure
all live in the `jfx.control` Scala.js components -- the same classes the Scala
demo mounts -- published as part of the linked runtime
`@anjunar/scalajs-jfx-bridge`. Adding this package does not add a second
implementation; `jfx-bridge` grew a `dependsOn(jfxControls)` edge and five
registry entries (`tabs`, `carousel`, `table-view`, `data-grid`,
`virtual-list-view`). The measured cost of that on the one linked artifact is in
[`JAVASCRIPT_API.md` §14](../../JAVASCRIPT_API.md).

```bash
npm install @anjunar/jfx-core @anjunar/jfx-controls @anjunar/scalajs-jfx-bridge @anjunar/scalajs-jfx
```

`@anjunar/scalajs-jfx` (the component CSS) is a peer here, not just a
convenience: the controls render with class names that come from the Scala
modules.

## Tabs

```ts
import { div, text } from "@anjunar/jfx-core";
import { tab, tabs } from "@anjunar/jfx-controls";

tabs(
  [
    tab("Overview", () => div(() => text("project overview"))),
    tab("Activity", () => div(() => text("recent activity"))),
  ],
  { renderMode: "keep-mounted" },
);
```

`renderMode` is `"active-only"` (default -- mount only the selected panel) or
`"keep-mounted"`. `selectedIndex` accepts a number or a `ReadOnlyProperty<number>`.

## Carousel

```ts
import { div, listProperty, text } from "@anjunar/jfx-core";
import { carousel } from "@anjunar/jfx-controls";

const slides = listProperty([
  { title: "Atlas" },
  { title: "Signal" },
  { title: "Harbor" },
]);

carousel(slides, (slide, index) => div(() => text(`${index + 1}. ${slide.title}`)), {
  autoAdvanceMs: 2600,
  ssrShowAllStates: true,
});
```

## Table, data grid, list

All three take a **source** and render through a callback. The source is either a
local `ListProperty<T>` from `@anjunar/jfx-core` or a `RemoteSource<T, Q>` --
sparsely loaded, the control asks for the ranges it needs.

```ts
import { listProperty, text } from "@anjunar/jfx-core";
import { column, tableView } from "@anjunar/jfx-controls";

interface Book { title: string; author: string; year: number }

const books = listProperty<Book>([/* ... */]);

tableView(
  books,
  [
    column("Title", (book) => text(book.title), { prefWidth: 300, sortable: true, sortKey: "title" }),
    column("Author", (book) => text(book.author), { prefWidth: 220 }),
    column("Year", (book) => text(String(book.year)), { prefWidth: 100 }),
  ],
  { rowHeight: 44, crawlable: true, crawlId: "books" },
);
```

A remote source:

```ts
import { remoteSource, tableView } from "@anjunar/jfx-controls";

interface Query { offset: number; limit: number; sorting?: readonly { field: string; ascending: boolean }[] }

const source = remoteSource<Book, Query>({
  initialQuery: { offset: 0, limit: 50 },
  initial: firstPage,                       // shown on the server -- there is no async mount point
  totalCount: 1000,
  rangeQuery: (q, offset, limit) => ({ ...q, offset, limit }),
  sortQuery: (q, sorting) => ({ ...q, offset: 0, sorting }),
  load: (q) => fetch(`/books?offset=${q.offset}&limit=${q.limit}`).then((r) => r.json()),
});

tableView(source, columns, { crawlable: true, crawlId: "books" });
```

`dataGrid(source, renderCell, options?)` and `virtualList(source, renderCell, options?)`
follow the same shape; their `renderCell` receives `item | null` -- `null` for a
position that exists but has not loaded yet.

`crawlable` / `crawlId` render a fixed first slice on the server with a "more"
link so a crawler can reach past the first screen. The link's path comes from the
surrounding `router()` -- it is only meaningful for a control rendered inside one.

### Not in this release

The facade is reactive-input only. Imperative handles (`carousel.next()`,
`tableView.select(item)`, `dataGrid.scrollTo(i)`), the `onRowDoubleClick` /
`selectedItem` readback, and `cellValueFactory` are not projected yet -- see
`CLAUDE_REVIEW_3.md`, Nachtrag Lauf 4.

## Tests

```bash
npm run verify   # typecheck + the bridge smoke test + the consumer test
```

The suite runs only against the really linked bridge -- there is no stub half,
because the stub runtime knows nothing about controls. Link it first:

```bash
sbtn "scalajs-jfx-bridge/fullLinkJS"
```
