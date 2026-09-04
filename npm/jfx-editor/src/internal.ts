/**
 * Shared plumbing for the editor facade. Same helper `@anjunar/jfx-forms`'s
 * own `internal.ts` provides -- kept as a local copy rather than a
 * dependency edge, since this package needs nothing else from it.
 */

/**
 * Drops `undefined` entries.
 *
 * The bridge reads top-level options by key presence
 * (`options.get("placeholder")`), so an explicit `placeholder: undefined`
 * would be seen as "set to undefined" instead of falling back to the
 * Scala-side default.
 */
export function defined(entries: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(entries)) {
    if (value !== undefined) out[key] = value;
  }
  return out;
}
