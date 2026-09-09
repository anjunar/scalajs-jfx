/**
 * A virtualized table with a column model. Mirrors `jfx.control.table.TableView`.
 *
 * Virtualization, row measurement, the sortable header, range loading and the
 * stable SSR / hydration structure stay in the Scala.js component. This is the
 * shape of a table declaration in TypeScript: a data source, a list of columns,
 * a few options. `table-view` is a registry entry in `jfx-bridge`
 * (`ControlFactories.scala`).
 */
import { component, currentScope, withScope } from "@anjunar/jfx-core";
import type { ComponentHandle, Reactive, ReadOnlyProperty, ScopeHandle } from "@anjunar/jfx-core";
import { body, defined, rowBody } from "./internal.js";
import type { Source, SortSpec } from "./data-source.js";

export interface ColumnDef<T> {
  readonly text: string;
  readonly prefWidth?: number;
  readonly minWidth?: number;
  readonly maxWidth?: number;
  /** Disables pointer/API resizing and automatic width compensation. Defaults to true. */
  readonly resizable?: Reactive<boolean>;
  /** Disables header drag/keyboard reordering, not programmatic moves. Defaults to true. */
  readonly reorderable?: Reactive<boolean>;
  /** Removes the column from layout and rendering without removing its definition. Defaults to true. */
  readonly visible?: Reactive<boolean>;
  /** Observes changes, not the initial value; use to write menu changes back to app state. */
  readonly onVisibilityChange?: (visible: boolean) => void;
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

/** State of one mounted row, including unloaded remote placeholders. */
export interface TableRowContext<T> {
  readonly item: ReadOnlyProperty<T | null>;
  readonly index: ReadOnlyProperty<number>;
  readonly empty: ReadOnlyProperty<boolean>;
  readonly selected: ReadOnlyProperty<boolean>;
  /** Logical row focus; independent of selection and whether the grid owns DOM focus. */
  readonly focused: ReadOnlyProperty<boolean>;
  /** Optional standard cells. Call at most once, synchronously in this row body or a nested element. */
  renderCells(): void;
}

export type TableSelectionMode = "single" | "multiple";
/** One term in an explicit remote sort order, resolved against the current visible columns. */
export interface TableSort {
  readonly columnIndex: number;
  readonly ascending: boolean;
}
export type ColumnResizePolicy = "unconstrained" | "all-columns" | "last-column" | "next-column"
  | "subsequent-columns" | "flex-next-column" | "flex-last-column";

export interface TableViewOptions<T = unknown> {
  /** Optional column chooser above the header. Requires a surrounding viewport. Default false. */
  readonly tableMenuButtonVisible?: Reactive<boolean>;
  /** Trigger text and menu accessible label. Default Columns. */
  readonly columnMenuText?: Reactive<string>;
  /** Defaults to flex-last-column. Constrained policies fit the viewport and hide horizontal overflow. */
  readonly columnResizePolicy?: Reactive<ColumnResizePolicy>;
  /** Defaults to single selection. Changes to single retain the lead selection. */
  readonly selectionMode?: Reactive<TableSelectionMode>;
  /** Replaces row content. Styles/events apply to the row; call row.renderCells() for standard columns. */
  readonly row?: (row: TableRowContext<T>) => void;
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

/** Runtime-owned row selection, row focus, navigation and refresh. Cell coordinates remain pending. */
export interface TableViewHandle<T = unknown> {
  /** Atomically replaces the whole remote order. Invalid, hidden, unsortable or duplicate
   * sort keys reject the whole command without changing state. Empty order requests unsorted.
   * Indices resolve at call time; the resulting field keys survive later column reordering.
   * Browser-only, with the same guards and return semantics as toggleSort.
   */
  setSortOrder(order: readonly TableSort[]): boolean;
  /** Reissues the current remote order (also empty/hidden/external terms), e.g. after a load failure.
   * True means requested, not completed; the remote source may deduplicate an in-flight request.
   */
  sort(): boolean;
  /** Requested remote order. Loading/error state belongs to the source, not an accepted-result snapshot. */
  readonly sorting: ReadOnlyProperty<readonly SortSpec[]>;
  /** Browser-only: cycle unsorted/ascending/descending; additive preserves other terms and priority.
   * False for local sources, invalid/hidden/unsortable columns, during SSR/hydration or after disposal.
   * True means a remote request was issued, not that loading succeeded. Resets paging/scroll to the start.
   */
  toggleSort(visibleColumnIndex: number, additive?: boolean): boolean;
  /** Clears all remote sort terms through the same command path. */
  clearSort(): boolean;
  /** Logical row focus. A known unloaded position has a null focusedItem. */
  readonly focusedIndex: ReadOnlyProperty<number>;
  readonly focusedItem: ReadOnlyProperty<T | null>;
  /** Changes only logical focus: no selection, scrolling, DOM focus or remote fetch.
   * Invalid indices clear focus; all focus operations are no-ops after disposal.
   */
  focusIndex(index: number): void;
  focusNext(): void;
  focusPrevious(): void;
  /** Rendered widths in visible-column order; independent read-only snapshots. */
  readonly columnWidths: ReadOnlyProperty<readonly number[]>;
  /** Resizes a visible column by a pixel delta; true if any movement was possible. */
  resizeColumn(visibleColumnIndex: number, delta: number): boolean;
  /** Browser-only: fit header + up to 100 mounted loaded cells; never fetches remote data.
   * Bounds and resize policy apply. Returns true on change or when queued during hydration.
   * Hidden/unmeasurable/locked columns, protected hosts and active composition are no-ops.
   */
  autoFitColumn(visibleColumnIndex: number): boolean;
  /** Browser-only: moves to a final visible index without recreating cells. During hydration,
   * the latest valid request waits until claiming completes. Hidden columns keep relative order.
   * Returns false for invalid/no-op requests, protected hosts or active IME composition.
   */
  moveColumn(fromVisibleIndex: number, toVisibleIndex: number): boolean;
  readonly selectionMode: ReadOnlyProperty<TableSelectionMode>;
  /** Sorted unique absolute positions. Item snapshots omit unloaded positions (no remote fetch). */
  readonly selectedIndices: ReadOnlyProperty<readonly number[]>;
  readonly selectedItems: ReadOnlyProperty<readonly T[]>;
  /** Lead selection in absolute view coordinates; an unloaded lead has a null item. */
  readonly selectedIndex: ReadOnlyProperty<number>;
  readonly selectedItem: ReadOnlyProperty<T | null>;
  /** Invalid positions clear selection. These methods are no-ops after unmount. */
  selectIndex(index: number): void;
  /** Selects the first matching item, or clears when absent. Does not fetch unloaded items. */
  selectItem(item: T): void;
  clearSelection(): void;
  setSelectionMode(mode: TableSelectionMode): void;
  clearAndSelect(index: number): void;
  clearIndex(index: number): void;
  isSelected(index: number): boolean;
  /** Adds valid indices, ignoring invalid/duplicate arguments. Last valid index becomes lead. */
  selectIndices(indices: readonly number[]): void;
  /** Inclusive start, exclusive end, forward or backward. Adds to existing selection. */
  selectRange(start: number, end: number): void;
  /** Multiple mode only; selects currently known positions without loading their items. */
  selectAll(): void;
  selectFirst(): void;
  selectLast(): void;
  selectNext(): void;
  selectPrevious(): void;
  /** Reveals an absolute row without selecting it or changing paging/scrolling mode.
   * Invalid/unknown positions are ignored. Known remote gaps load through the normal viewport.
   * Browser-only; during hydration/hidden layout the latest valid request waits for a measurable viewport.
   */
  scrollToIndex(index: number): void;
  /** Reveals the first loaded matching item; absent items are ignored, searching never fetches data. */
  scrollToItem(item: T): void;
  /** Reveals a current visible column with minimal horizontal movement. Oversized columns align
   * at their start. Does not change row position, selection, focus, widths or resize policy.
   * Browser-only; the latest valid request waits for hydration/measurable layout, following the
   * requested column across reordering. Hidden/removed/invalid targets and disposed tables are no-ops.
   */
  scrollToColumnIndex(visibleColumnIndex: number): void;
  /** Rebuilds visible cell content to pick up snapshot mutations. Does not request a remote reload. */
  refresh(): void;
  readonly isDisposed: boolean;
}

/** Mounts a table and returns its runtime-owned handle. */
export function tableView<T, Q = unknown>(
  source: Source<T, Q>,
  columns: readonly ColumnDef<T>[],
  options: TableViewOptions<T> = {}
): TableViewHandle<T> {
  let handle: TableViewHandle<T> | undefined;
  component(
    "table-view",
    defined({
      source,
      tableMenuButtonVisible: options.tableMenuButtonVisible,
      columnMenuText: options.columnMenuText,
      columnResizePolicy: options.columnResizePolicy,
      selectionMode: options.selectionMode,
      row: options.row
        ? (row: Omit<TableRowContext<T>, "renderCells">, self: ComponentHandle, scope: ScopeHandle,
           cells: (scope: ScopeHandle) => void) => withScope(scope, self, () => options.row!({
             item: row.item, index: row.index, empty: row.empty, selected: row.selected, focused: row.focused,
             renderCells: () => cells(currentScope()),
           }))
        : undefined,
      receiveHandle: (value: TableViewHandle<T>) => { handle = value; },
      columns: columns.map((col) => ({
        text: col.text,
        prefWidth: col.prefWidth,
        minWidth: col.minWidth,
        maxWidth: col.maxWidth,
        resizable: col.resizable,
        reorderable: col.reorderable,
        visible: col.visible,
        onVisibilityChange: col.onVisibilityChange,
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
