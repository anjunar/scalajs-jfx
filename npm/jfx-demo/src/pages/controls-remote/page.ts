import { classes, div, text } from "@anjunar/jfx-core";
import { column, remoteSource, tableView } from "@anjunar/jfx-controls";
import type { RemotePage, RemoteSource, SortSpec } from "@anjunar/jfx-controls";

interface Row {
  readonly id: number;
  readonly name: string;
}

interface Query {
  readonly offset: number;
  readonly limit: number;
  readonly sorting: readonly SortSpec[];
}

/**
 * Generated, not fetched -- a few thousand rows answered page by page with a
 * setTimeout, so this page stays offline and reproducible in SSR (no real
 * network call). See CLAUDE_DEMO_PLAN.md §5.
 */
const TOTAL_ROWS = 5000;
const PAGE_SIZE = 50;

function slice(query: Query): readonly Row[] {
  const rows: Row[] = [];
  for (let i = 0; i < query.limit && query.offset + i < TOTAL_ROWS; i++) {
    const id = query.offset + i;
    rows.push({ id, name: `Item ${id.toString().padStart(4, "0")}` });
  }
  const nameSort = query.sorting.find((term) => term.field === "name");
  if (nameSort !== undefined && !nameSort.ascending) rows.reverse();
  return rows;
}

function loadPage(query: Query): Promise<RemotePage<Row, Query>> {
  return new Promise((resolve) => {
    setTimeout(() => {
      const items = slice(query);
      const nextOffset = query.offset + query.limit;
      resolve({
        items,
        offset: query.offset,
        totalCount: TOTAL_ROWS,
        hasMore: nextOffset < TOTAL_ROWS,
        nextQuery: nextOffset < TOTAL_ROWS ? { ...query, offset: nextOffset } : undefined,
      });
    }, 30);
  });
}

export function controlsRemotePage(initialOffset = 0): void {
  const normalizedOffset = Math.max(0, Math.floor(initialOffset / PAGE_SIZE) * PAGE_SIZE);
  const initialQuery: Query = { offset: normalizedOffset, limit: PAGE_SIZE, sorting: [] };
  const source: RemoteSource<Row, Query> = remoteSource({
    load: loadPage,
    initialQuery,
    initial: slice(initialQuery),
    initialOffset: normalizedOffset,
    totalCount: TOTAL_ROWS,
    rangeQuery: (query, offset, limit) => ({ ...query, offset, limit }),
    sortQuery: (query, sorting) => ({ ...query, sorting }),
  });

  div(() => {
    classes("h-80");
    tableView(
      source,
      [
        column("Id", (row) => text(String(row.id)), { prefWidth: 100 }),
        column("Name", (row) => text(row.name), { prefWidth: 220, sortable: true, sortKey: "name" }),
      ],
      { rowHeight: 36, paging: true, pageSize: 50, crawlable: true, crawlId: "remote-rows" }
    );
  });
}
