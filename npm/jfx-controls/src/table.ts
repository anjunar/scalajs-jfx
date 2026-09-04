/**
 * A virtualized table with a column model. Mirrors `jfx.control.table.TableView`.
 *
 * Virtualization, row measurement, the sortable header, range loading and the
 * stable SSR / hydration structure stay in the Scala.js component. This is the
 * shape of a table declaration in TypeScript: a data source, a list of columns,
 * a few options. `table-view` is a registry entry in `jfx-bridge`
 * (`ControlFactories.scala`).
 */
import { component } from "@anjunar/jfx-core";
import { body, defined, rowBody } from "./internal.js";
import type { Source } from "./data-source.js";

export interface ColumnDef<T> {
  readonly text: string;
  readonly prefWidth?: number;
  /** Enables the sort toggle in this column's header. Needs `sortKey` and a `sortQuery` on the source. */
  readonly sortable?: boolean;
  /** The field name passed back to the source's `sortQuery`. */
  readonly sortKey?: string;
  /** Composes one cell's content for `row`, with the core DSL. */
  readonly cell: (row: T) => void;
}

/** Builds one {@link ColumnDef}. */
export function column<T>(
  text: string,
  cell: (row: T) => void,
  options: Omit<ColumnDef<T>, "text" | "cell"> = {}
): ColumnDef<T> {
  return { text, cell, ...options };
}

export interface TableViewOptions {
  readonly rowHeight?: number;
  readonly showHeader?: boolean;
  readonly showFooter?: boolean;
  /** Page through the data instead of scrolling it. */
  readonly paging?: boolean;
  readonly pageSize?: number;
  /**
   * Render a fixed slice on the server with a pager link, so a crawler can reach
   * past the first screen. Needs `crawlId`. Only meaningful for a table rendered
   * inside a `router()` shell, which provides the current URL.
   */
  readonly crawlable?: boolean;
  readonly crawlId?: string;
  /** A content header that scrolls with the rows, below the fixed column header. */
  readonly header?: () => void;
  /** Shown while the table has no rows. */
  readonly placeholder?: () => void;
}

/** Mounts a table over `source` with `columns`. */
export function tableView<T, Q = unknown>(
  source: Source<T, Q>,
  columns: readonly ColumnDef<T>[],
  options: TableViewOptions = {}
): void {
  component(
    "table-view",
    defined({
      source,
      columns: columns.map((col) => ({
        text: col.text,
        prefWidth: col.prefWidth,
        sortable: col.sortable,
        sortKey: col.sortKey,
        cell: rowBody(col.cell),
      })),
      rowHeight: options.rowHeight,
      showHeader: options.showHeader,
      showFooter: options.showFooter,
      paging: options.paging,
      pageSize: options.pageSize,
      crawlable: options.crawlable,
      crawlId: options.crawlId,
      header: options.header ? body(options.header) : undefined,
      placeholder: options.placeholder ? body(options.placeholder) : undefined,
    })
  );
}
