# jfx-demo

The documentation site for the `@anjunar/jfx-*` npm family: one route per
capability, the running component next to the exact source that produced it.
Vite dev server, Express SSR, hydration, client-side routing -- the same
shape as the repo root's own `application/` + `server/server.mjs`, minus the
Scala.js linker.

**Real means real.** Every import here goes through the packages' public
`exports`; nothing reaches into a neighbouring directory. That was not true
until the npm modularisation -- the entry points used to import
`../../jfx/src/index.js` because Vite's SSR runner gave the same file two module
instances, and two instances are two `installRuntime` slots. `resolve.dedupe` in
`vite.config.ts` fixes that at the cause, and `npm run verify:runtime` proves it
rather than assuming it.

Not published; `private: true`. See [`JAVASCRIPT_API.md`](../../JAVASCRIPT_API.md) §13.

## Setup

```bash
# from the repo root -- npm workspaces install every package at once
sbtn "scalajs-jfx-bridge/fullLinkJS"    # the only bridge artifact there is (npm/scalajs-jfx-bridge/README.md)
npm install
```

## Run

```bash
npm run dev              # http://localhost:5174, Vite dev middleware + Express SSR
npm run build && npm start   # production build, served from dist/

npm run demo             # Node + stub runtime, the @anjunar/jfx-core pages only
npm run demo:bridge      # Node + the linked Scala.js runtime, every non-router page
npm run demo:scope       # the three scope rules, demonstrated

npm run verify           # typecheck, build, the one-runtime proof, and every page's SSR proof
```

The Node runners live here rather than in the library because they are
consumers too: they reach `@anjunar/jfx-core` by package name, so they exercise
the same import path a stranger would. Their runnable page list comes from
`src/app/page-manifest.ts`, which imports only build-safe `page.ts` modules;
they do not import `entry.doc`, because `doc.ts` uses Vite-only `?jfx-code`
imports.

## The catalog

`src/app/catalog.ts` is the one place every documented route, its nav entry and
its search-index entry come from. The home page's `packageTiles` export is also
the source for the package groups shown by the shell. The build-safe
`src/app/page-manifest.ts` supplies runnable page metadata to the Node runners
and is joined back to the catalog for route/title/package/runtime identity.
`src/app/routes.ts` turns the catalog into the router's route table and nothing
else.

## Adding a page

Two files, plus one catalog entry:

```
src/pages/<name>/page.ts   # the example itself: DSL, and only @anjunar/jfx-* imports
src/pages/<name>/doc.ts    # title, prose, the code block, calls page.ts
```

`page.ts` must import exclusively from `@anjunar/jfx-*` packages -- no
relative imports, no `?jfx-code`, no CSS. `npm run build:node` (a separate,
narrower `tsc` program, see `tsconfig.node.json`) is what enforces this: it
compiles every `src/pages/**/page.ts` on its own, so a stray import fails the
build there even if the main `tsc` program tolerates it. `doc.ts` shows the
exact source that runs by importing it with a `?jfx-code` suffix
(`tools/vite-plugin-jfx-code.ts` tokenizes it at build time -- see that file's
doc comment, and `CLAUDE_DEMO_PLAN.md`'s E-3):

```ts
import snippet from "./page.ts?jfx-code";              // the whole file
import snippet from "./page.ts?jfx-code=composer";     // just the region
                                                        // marked `// #region composer` / `// #endregion`
```

Then one entry in `src/app/catalog.ts`'s `catalogEntries` array (`summary`,
`keywords`, `doc`) and, for a page rendered by a Node runner, one matching entry
in `src/app/page-manifest.ts` (`path`, `title`, `pkg`, runtime and page body).
The catalog derives runnable identity from that manifest and rejects missing
matches during startup. Pages using controls, viewport, forms or router belong
to the bridge runtime; core-only pages can also run against the stub.

## Design

`src/styles/style.css` is the cascade entry, in the order of authority:

```css
@import "tailwindcss";
@import "@anjunar/ui";                       /* the --aj-* token grammar */
@import "@anjunar/scalajs-jfx/index.css";    /* what the library components render */

@import "./theme.css";   /* the only place this project sets a token value */
@import "./base.css";
@import "./docs/index.css";
@import "./pages/index.css";
```

`@anjunar/ui` is a real dependency of this package (see `package.json`) and
supplies every `--aj-*` token the library CSS reads, for both light and dark
(`html[data-theme]`) -- there is nothing to fall back to a browser default
for. `src/styles/theme.css` sets only the handful of tokens `@anjunar/ui`
doesn't define at all (`--aj-code-*`, for the syntax-highlighted code block).

The i18n provider in `src/app/i18n.ts` uses English and German and sits above
the router in both SSR and hydration. Each page keeps its own
`translations.ts`; the merged catalog resolves page metadata, navigation and
the locale-aware URL prefix (`/en/...` or `/de/...`).

## What's real, what's a stand-in

- `entry-client.ts` / `entry-server.ts` call `hydrate()` / `renderToString()`
  through `bridgeRuntime` -- the actual Scala.js bridge, not a stub.
- `src/app/routes.ts` is a real `RouteDefinition[]` fed to `router(appRoutes,
  config, appShell)` -- `view()`, a nested route through `routerOutlet()`
  (`/router/nested`), a parametrized route with a constraint
  (`/router/params/:id`), and an `errorRoute("/404", 404)`. Navigation is
  client-side through `routerLink`; Express renders the first request and
  serves assets, it does not route.
- Locale-aware routing and message-based i18n are wired through the same
  provider: `/en/...` and `/de/...` routes resolve their page catalogs, and the
  shell locale switch preserves the current route. The document head is
  rendered by `src/app/document.ts`, including the active language attribute.
