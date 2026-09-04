/**
 * The two renderer-driven virtualized collections:
 *
 *  - `dataGrid` -- fixed-size cells in a responsive column count
 *    (`jfx.control.datagrid.DataGrid`)
 *  - `virtualList` -- measured row heights, one column
 *    (`jfx.control.virtuallist.VirtualListView`)
 *
 * Both take a {@link Source} and one renderer per item. Virtualization, range
 * loading and the SSR / hydration structure stay in the Scala.js components;
 * `data-grid` and `virtual-list-view` are registry entries in `jfx-bridge`
 * (`ControlFactories.scala`). The renderer's item is `null` for a position that
 * exists but has not loaded yet.
 */
import { component } from "@anjunar/jfx-core";
import { body, defined, itemBody } from "./internal.js";
import type { Source } from "./data-source.js";

export interface DataGridOptions {
  readonly itemWidthPx?: number;
  readonly itemHeightPx?: number;
  readonly gapPx?: number;
  readonly overscanRows?: number;
  readonly prefetchItems?: number;
  readonly paging?: boolean;
  readonly pageSize?: number;
  readonly crawlable?: boolean;
  readonly crawlId?: string;
  readonly header?: () => void;
  readonly loadingPlaceholder?: () => void;
  readonly emptyPlaceholder?: () => void;
}

export function dataGrid<T, Q = unknown>(
  source: Source<T, Q>,
  renderCell: (item: T | null, index: number) => void,
  options: DataGridOptions = {}
): void {
  component(
    "data-grid",
    defined({
      source,
      cellRenderer: itemBody(renderCell),
      itemWidthPx: options.itemWidthPx,
      itemHeightPx: options.itemHeightPx,
      gapPx: options.gapPx,
      overscanRows: options.overscanRows,
      prefetchItems: options.prefetchItems,
      paging: options.paging,
      pageSize: options.pageSize,
      crawlable: options.crawlable,
      crawlId: options.crawlId,
      header: options.header ? body(options.header) : undefined,
      loadingPlaceholder: options.loadingPlaceholder ? body(options.loadingPlaceholder) : undefined,
      emptyPlaceholder: options.emptyPlaceholder ? body(options.emptyPlaceholder) : undefined,
    })
  );
}

export interface VirtualListOptions {
  readonly estimateHeightPx?: number;
  readonly overscanPx?: number;
  readonly prefetchItems?: number;
  readonly paging?: boolean;
  readonly pageSize?: number;
  readonly crawlable?: boolean;
  readonly crawlId?: string;
  readonly header?: () => void;
}

export function virtualList<T, Q = unknown>(
  source: Source<T, Q>,
  renderCell: (item: T | null, index: number) => void,
  options: VirtualListOptions = {}
): void {
  component(
    "virtual-list-view",
    defined({
      source,
      cellRenderer: itemBody(renderCell),
      estimateHeightPx: options.estimateHeightPx,
      overscanPx: options.overscanPx,
      prefetchItems: options.prefetchItems,
      paging: options.paging,
      pageSize: options.pageSize,
      crawlable: options.crawlable,
      crawlId: options.crawlId,
      header: options.header ? body(options.header) : undefined,
    })
  );
}
