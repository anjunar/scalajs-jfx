/**
 * Shared plumbing for the viewport facades.
 *
 * Mirrors `@anjunar/jfx-controls`'s own `internal.ts`, but simpler: every option
 * here maps 1:1 onto a bridge dictionary key, and every body is a plain
 * `() => void` that the public `component()` helper (`@anjunar/jfx-core`)
 * already wraps into the `(scope) => void` the bridge runs -- no per-item
 * renderer indirection like `itemBody`/`rowBody` is needed anywhere in this
 * package.
 */

/**
 * Drops `undefined` entries.
 *
 * The bridge reads these options by key presence (`options.get("widthPx")`),
 * so an explicit `widthPx: undefined` would be seen as "set to undefined" and
 * coerced to a bad number instead of falling back to the Scala-side default.
 */
export function defined(entries: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(entries)) {
    if (value !== undefined) out[key] = value;
  }
  return out;
}
