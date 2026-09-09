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
import type { Reactive, ReadOnlyProperty } from "@anjunar/jfx-core";
import { body, defined, rowBody } from "./internal.js";
import type { Source } from "./data-source.js";

export interface ColumnDef<T> {
  readonly text: string;
  readonly prefWidth?: number;
  /** Removes the column from layout and rendering without removing its definition. Defaults to true. */
  readonly visible?: Reactive<boolean>;
  /** Enables the sort toggle in this column's header. Needs `sortKey` and a `sortQuery` on the source. */
  readonly sortable?: boolean;
  /** The field name passed back to the source's `sortQuery`. */
  readonly sortKey?: string;
  /** Composes one cell's content for `row`, with the core DSL. */
  readonly cell?: (row: T) => void;
  /** A snapshot or observed cell value. Used by the default text cell when `cell` is absent. */
  readonly value?: (row: T) => Reactive<unknown>;
  /** Prefer `valueColumn` for a renderer whose observed value retains its concrete type. */
  readonly valueCell?: (value: ReadOnlyProperty<unknown>, row: T) => void;
}

export interface ValueColumnOptions<S, V> extends Omit<ColumnDef<S>, "text" | "cell" | "value" | "valueCell"> {
  readonly cell?: (value: ReadOnlyProperty<V | null>, row: S) => void;
}

/** A typed value column backed by the runtime's observed TableCell binding. */
export function valueColumn<S, V>(
  text: string,
  value: (row: S) => Reactive<V>,
  options: ValueColumnOptions<S, V> = {}
): ColumnDef<S> {
  const { cell, ...metadata } = options;
  const result: ColumnDef<S> = {
    text,
    value,
    ...metadata,
  };
  return cell
    ? { ...result, valueCell: (observed, row) => cell(observed as ReadOnlyProperty<V | null>, row) }
    : result;
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
  /** Keep paging after hydration. Omit or set to false to use browser scrolling. */
  readonly paging?: boolean;
  readonly pageSize?: number;
  /** Minimum content-header height in table rows. */
  readonly headerRows?: number;
  /**
   * Render a fixed slice on the server with a pager link, so a crawler can reach
   * past the first screen. Needs `crawlId`. Only meaningful for a table rendered
   * inside a `router()` shell, which provides the current URL.
   */
  readonly crawlable?: boolean;
  readonly crawlId?: string;
  /** A content header that scrolls with the rows, below the fixed column header. */
  readonly header?: () => void;
  /** Shown while the table has no rows or no visible columns. */
  readonly placeholder?: () => void;
}

/** Runtime-owned single-selection and refresh operations. Multi-selection and scrolling APIs remain pending. */
export interface TableViewHandle<T = unknown> {
  /** Single-row selection in absolute view coordinates; unloaded positions have a null item. */
  readonly selectedIndex: ReadOnlyProperty<number>;
  readonly selectedItem: ReadOnlyProperty<T | null>;
  /** Invalid positions clear selection. These methods are no-ops after unmount. */
  selectIndex(index: number): void;
  /** Selects the first matching item, or clears when absent. Does not fetch unloaded items. */
  selectItem(item: T): void;
  clearSelection(): void;
  /** Rebuilds visible cell content to pick up snapshot mutations. Does not request a remote reload. */
  refresh(): void;
  readonly isDisposed: boolean;
}

/** Mounts a table and returns its runtime-owned handle. */
export function tableView<T, Q = unknown>(
  source: Source<T, Q>,
  columns: readonly ColumnDef<T>[],
  options: TableViewOptions = {}
): TableViewHandle<T> {
  let handle: TableViewHandle<T> | undefined;
  component(
    "table-view",
    defined({
      source,
      receiveHandle: (value: TableViewHandle<T>) => { handle = value; },
      columns: columns.map((col) => ({
        text: col.text,
        prefWidth: col.prefWidth,
        visible: col.visible,
        sortable: col.sortable,
        sortKey: col.sortKey,
        cell: col.cell ? rowBody(col.cell) : undefined,
        value: col.value,
        valueCell: col.valueCell
          ? (value: ReadOnlyProperty<unknown>, row: T) => body(() => col.valueCell!(value, row))
          : undefined,
      })),
      rowHeight: options.rowHeight,
      showHeader: options.showHeader,
      showFooter: options.showFooter,
      paging: options.paging,
      pageSize: options.pageSize,
      headerRows: options.headerRows,
      crawlable: options.crawlable,
      crawlId: options.crawlId,
      header: options.header ? body(options.header) : undefined,
      placeholder: options.placeholder ? body(options.placeholder) : undefined,
    })
  );
  if (handle === undefined) throw new Error("The installed runtime does not provide a TableView handle.");
  return handle;
}
