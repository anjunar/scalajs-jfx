/// <reference types="vite/client" />

/**
 * `?ui-code` imports are answered by tools/vite-plugin-ui-code.ts, not by
 * Vite's own asset pipeline -- these declarations are what let `tsc` (which
 * never runs the plugin) accept them. See CLAUDE_DEMO_PLAN.md E-3.
 *
 * TypeScript's ambient wildcard modules allow exactly one `*` per pattern,
 * so the whole-file form (an arbitrary specifier ending in `?ui-code`) and
 * the per-region form need separate declarations: a region name is
 * arbitrary trailing text after a fixed `=`, and every doc.ts writes that
 * import as literally `./page.ts?ui-code=<region>` (or, for the nested
 * router example, `./detail.ts?ui-code=<region>`) -- one sibling file per
 * page directory, per CLAUDE_DEMO_PLAN.md E-2 -- so the *specifier text*
 * itself, not just its resolved path, is one of a small fixed set of
 * prefixes.
 */
declare module "*?ui-code" {
  import type { CodeSnippet } from "./docs/code-block.js";
  const snippet: CodeSnippet;
  export default snippet;
}

declare module "./page.ts?ui-code=*" {
  import type { CodeSnippet } from "./docs/code-block.js";
  const snippet: CodeSnippet;
  export default snippet;
}

declare module "./detail.ts?ui-code=*" {
  import type { CodeSnippet } from "./docs/code-block.js";
  const snippet: CodeSnippet;
  export default snippet;
}
