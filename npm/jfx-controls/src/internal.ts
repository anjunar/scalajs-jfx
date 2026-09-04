/**
 * Shared plumbing for the control facades.
 *
 * Every control is a registry entry in `jfx-bridge` (`ControlFactories.scala`).
 * The consumer writes ambient-scope DSL callbacks -- `() => void`,
 * `(item, index) => void`, `(row) => void` -- and this module turns each into the
 * `(scope) => void` the bridge runs against a fresh handle, the same wrap
 * `@anjunar/jfx-router`'s `toFacadeRoute` applies to a route loader.
 */
import { withScope } from "@anjunar/jfx-core";
import type { ScopeHandle } from "@anjunar/jfx-core";

/** A body the bridge runs: it opens the ambient scope and calls the consumer's callback. */
export type ScopeBody = (scope: ScopeHandle) => void;

/** Wraps a plain `() => void` DSL body. */
export function body(run: () => void): ScopeBody {
  return (scope) => withScope(scope, null, run);
}

/** Wraps an `(item, index) => void` cell / slide renderer into `(item, index) => ScopeBody`. */
export function itemBody<T>(
  render: (item: T, index: number) => void
): (item: T, index: number) => ScopeBody {
  return (item, index) => (scope) => withScope(scope, null, () => render(item, index));
}

/** Wraps a `(row) => void` column cell into `(row) => ScopeBody`. */
export function rowBody<T>(render: (row: T) => void): (row: T) => ScopeBody {
  return (row) => (scope) => withScope(scope, null, () => render(row));
}

/**
 * Drops `undefined` entries.
 *
 * The bridge reads top-level control options by key presence
 * (`options.get("rowHeight")`), so an explicit `rowHeight: undefined` would be
 * seen as "set to undefined" and coerced to `NaN`. Column sub-objects use
 * `js.UndefOr` and do not need this.
 */
export function defined(entries: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(entries)) {
    if (value !== undefined) out[key] = value;
  }
  return out;
}
