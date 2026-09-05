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

## SSR and non-JavaScript behavior

SSR renders the visible or crawl slice. Hydration adds measurement, scrolling, range loading, sorting, and efficient visible-window updates. A crawler cannot scroll; use crawlability or paging when deeper collection content must be addressable without JavaScript.

## API overview

- `tab`, `tabs`, `carousel`
- `column`, `tableView`, `dataGrid`, `virtualList`
- `remoteSource`, `RemoteSource`, `RemotePage`, `SortSpec`
- `TabsOptions`, `CarouselOptions`, `TableViewOptions`, `DataGridOptions`, `VirtualListOptions`

## Related modules

- [`@anjunar/jfx-core`](../jfx-core/README.md) provides state and render callbacks.
- [`@anjunar/jfx-viewport`](../jfx-viewport/README.md) provides the global UI layer.
