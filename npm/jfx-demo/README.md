# jfx-demo

A real consumer app for `@anjunar/jfx-core` + `@anjunar/jfx-router` +
`@anjunar/scalajs-jfx-bridge`: Vite dev server, Express SSR, hydration, and
client-side routing -- the same shape as the repo root's own `application/` +
`server/server.mjs`, minus the Scala.js linker.

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

npm run demo             # Node + stub runtime, prints the SSR output
npm run demo:bridge      # Node + the linked Scala.js runtime, same pages
npm run demo:scope       # the three scope rules, demonstrated

npm run verify           # typecheck, build, and the one-runtime proof
```

The three Node runners live here rather than in the library because they are
consumers too: they reach `@anjunar/jfx-core` by package name, so they exercise
the same import path a stranger would.

## Why Express

Vite bundles and serves assets; it does not run an arbitrary SSR route on its
own. In dev mode, `createServer({ middlewareMode: true })` turns Vite into
middleware that a real HTTP server owns -- Express here, exactly as in
`server/server.mjs`. In production, `vite build --ssr` produces
`dist/server/entry-server.js`, and Express just imports and calls its
`render()`.

## What's real, what's a stand-in

- `entry-client.ts` / `entry-server.ts` call `hydrate()` / `renderToString()`
  through `bridgeRuntime` -- the actual Scala.js bridge, not a stub.
- `src/demo.css` is a few rules for the demo's own class names, not a design
  system. `@anjunar/ui`, which `@anjunar/scalajs-jfx`'s CSS reads design
  tokens from, isn't installed here (it isn't part of this repo), so
  `@anjunar/scalajs-jfx/index.css` still loads but its custom properties fall
  back to browser defaults.
- `src/routes.ts` is a real `RouteDefinition[]` fed to `router(appRoutes,
  config, appShell)` -- `view()`, a nested route through `routerOutlet()`, an
  `errorRoute("/404", 404)`. Navigation is client-side through `routerLink`;
  Express renders the first request and serves assets, it does not route. The
  Node runners render the page bodies bare, with no shell and no router.
- No i18n, no document head management, and the pages are fragments mounted
  into a hand-written `index.html`, not a full JFX3 document the way
  `AppDocument` renders one in the Scala app. See JAVASCRIPT_API.md §6 for
  what a full document boundary would need.
