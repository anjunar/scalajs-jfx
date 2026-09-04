/**
 * The data source a virtualized control reads from.
 *
 * Two shapes, the same as on the Scala side (`jfx.core.state.ListDataSource`):
 *
 *  - a `ListProperty<T>` from `@anjunar/jfx-core` -- a local, fully materialized
 *    list. `ListProperty` already *is* a `ListDataSource`, so nothing wraps it.
 *  - a {@link RemoteSource} -- a sparsely loaded list. The control asks for the
 *    ranges it needs; the loader returns pages. The query object's shape is
 *    yours: the framework only carries it back to your `load`.
 *
 * `remoteSource()` is an identity helper -- it exists for type inference and to
 * read well at the call site. The object it returns is handed straight to the
 * bridge, which builds a `jfx.core.remote.RemoteListProperty` around it.
 */
import type { ListProperty } from "@anjunar/jfx-core";

/** One sort term. Mirrors `jfx.core.remote.RemoteSort`. */
export interface SortSpec {
  readonly field: string;
  readonly ascending: boolean;
}

/** One loaded page. Mirrors `jfx.core.remote.RemotePage`. */
export interface RemotePage<T, Q = unknown> {
  readonly items: readonly T[];
  /** Absolute index of `items[0]`. */
  readonly offset?: number;
  /** Total row count, once known -- lets the control size its scrollbar. */
  readonly totalCount?: number;
  /** The query for the next page, or omit when there is none. */
  readonly nextQuery?: Q;
  readonly hasMore?: boolean;
}

export interface RemoteSource<T, Q = unknown> {
  /** Loads one page for a query. */
  readonly load: (query: Q) => Promise<RemotePage<T, Q>>;
  /** The query for the first page. */
  readonly initialQuery: Q;
  /**
   * The first page, already in hand. Server rendering shows exactly this slice
   * -- there is no point at which the bridge can await `load` synchronously
   * while mounting.
   */
  readonly initial?: readonly T[];
  /** Absolute index represented by `initial[0]` when SSR starts on a later page. */
  readonly initialOffset?: number;
  readonly totalCount?: number;
  /** `(query, offset, limit) => query` -- enables range loading while scrolling. */
  readonly rangeQuery?: (query: Q, offset: number, limit: number) => Q;
  /** `(query, sorting) => query` -- enables the sortable column header on a table. */
  readonly sortQuery?: (query: Q, sorting: readonly SortSpec[]) => Q;
}

export type Source<T, Q = unknown> = ListProperty<T> | RemoteSource<T, Q>;

/** Identity helper: returns `spec`, typed. Reads well at the call site and pins `Q`. */
export function remoteSource<T, Q = unknown>(spec: RemoteSource<T, Q>): RemoteSource<T, Q> {
  return spec;
}
