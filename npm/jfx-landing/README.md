# JFX landing page

A private npm workspace for the product page, built with Vite and Express. The page has its own layout, English/German content, shared Atlas/Flora/Terra/Ember styles, and explicit light/dark selection. The compact sticky header keeps Docs and GitHub visible. Its native Menu disclosure contains section links and four named preference controls.

## Run

From the repository root, install with `npm ci` and link the runtime once with `sbt --server "scalajs-jfx-bridge/fullLinkJS"`.

From this directory:

- `npm run dev`: Vite middleware and server-rendered pages at `http://127.0.0.1:3316/`.
- `npm run build`: client bundle, server bundle, then prerendered English/German pages and downloadable starters in `dist/static`.
- `npm start`: production SSR using the built bundles.
- `npm run verify`: build, document/source/asset checks, and development/production server checks for both languages and every design/scheme combination.

`PORT` changes the server port. `SCALA_DEMO_URL` and `TYPESCRIPT_DEMO_URL` optionally point standalone demo links to local demos; by default they lead to the published demos. For a fully local assembled site, run `npm run check:pages` and `npm run preview:pages` at the repository root. This serves the exact Pages structure at `http://127.0.0.1:3320/scalajs-jfx/`.

## Ownership

- `src/page.mjs` renders the product document and downloadable starter contents. `vite.config.mjs` supplies version metadata and source files from the repository through a virtual module, so development and production use identical inputs.
- `src/previews.mjs` renders real JFX form, table, editor and counter previews on the server. These are library output, not hand-drawn widget imitations.
- `src/client.mjs` owns the header menu, appearance/language selectors, copy actions and activation button. `src/hydrate.mjs` is a dynamic import: the main page does not eagerly load the JFX runtime. It hydrates exactly the counter tree generated on the server.
- `src/style.css` consumes `@anjunar/scalajs-jfx/index.css` and defines the product layout in the shared cascade. It reuses the shared palette without a system-theme query.
- `src/designs.css` interprets the Constitution's landing slots for each design: Atlas's register, Flora's generous surfaces, Terra's editorial opening and Ember's framed tools. The palette, spacing and motion come from the shared tokens. Landing-only overrides use system interface/headline fonts, interactive focus and the contrast-tested muted-text color for borders. See [CONSTITUTION.md](CONSTITUTION.md) for the mapping and checked scope.
- `src/presentation.css` supplies an optional, height-aware presentation layout with naturally growing chapters. `src/presentation.mjs` persists the view choice and restores it before paint. Reading is the default; both modes retain native scrolling. See [FULLPAGE.md](FULLPAGE.md) for the interaction contract and verification.
- `src/messages.de.json` translates page copy; `src/ui-text.mjs` translates client interaction messages. `src/localize.mjs` walks the parsed document, preserving code and the hydratable example. Source snippets and sample data deliberately remain unchanged.

English is available at `/` and `/en/`, German at `/de/`. Each is prerendered, so language selection also works on static hosting. The language control navigates to the corresponding document and preserves design, scheme, query parameters and hash. Its chosen locale is represented by the URL; design and scheme use the same localStorage keys as the demos. A language change starts a new document; switching designs updates the existing page and retains the counter.

With JavaScript disabled, content, links and source downloads remain available. Preference controls and the counter are inert. Add `?nojs` when using the included servers to verify that behavior with a restrictive Content-Security-Policy.

## Pages integration

`tools/build-pages.mjs` builds both demos and this workspace, then assembles their outputs. `--check` validates all local destinations in `dist/pages` without changing `docs`; the normal build retains the existing behavior of moving the validated site to `docs`. No publication occurs merely by building this package.
