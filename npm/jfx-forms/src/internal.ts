/**
 * Shared plumbing for the forms facades.
 *
 * Every form component is a registry entry in `jfx-bridge`
 * (`FormFactories.scala`). The consumer writes ambient-scope DSL callbacks --
 * `() => void`, `(index, scope) => void`, `(item, selected) => (scope) => void`
 * -- and this module turns each into the `(scope) => void` shape the bridge
 * runs against a fresh handle, the same wrap `@anjunar/jfx-controls`'s own
 * `internal.ts` applies to a cell renderer.
 */
import { withScope } from "@anjunar/jfx-core";
import type { ReadOnlyProperty, ScopeHandle } from "@anjunar/jfx-core";

/** A body the bridge runs: it opens the ambient scope and calls the consumer's callback. */
export type ScopeBody = (scope: ScopeHandle) => void;

/** Wraps a plain `() => void` DSL body. */
export function body(run: () => void): ScopeBody {
  return (scope) => withScope(scope, null, run);
}

/** Wraps an `(index) => void` array-item renderer into `(index) => ScopeBody`. */
export function indexBody(render: (index: number) => void): (index: number) => ScopeBody {
  return (index) => (scope) => withScope(scope, null, () => render(index));
}

/** Wraps an `(item, selected) => void` combo-box item renderer into its curried bridge shape. */
export function itemSelectedBody<T>(
  render: (item: T, selected: ReadOnlyProperty<boolean>) => void
): (item: T, selected: ReadOnlyProperty<boolean>) => ScopeBody {
  return (item, selected) => (scope) => withScope(scope, null, () => render(item, selected));
}

/**
 * Drops `undefined` entries.
 *
 * The bridge reads top-level options by key presence (`options.get("model")`),
 * so an explicit `model: undefined` would be seen as "set to undefined"
 * instead of falling back to the Scala-side default.
 */
export function defined(entries: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(entries)) {
    if (value !== undefined) out[key] = value;
  }
  return out;
}
