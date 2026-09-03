# jfx-demo

A real consumer app for `@anjunar/jfx` + `@anjunar/scalajs-jfx-bridge`: Vite
dev server, Express SSR, hydration -- the same shape as the repo root's own
`application/` + `server/server.mjs`, minus the Scala.js linker. It renders
`npm/jfx/demo/pages.ts`'s `statePage()`, unmodified, the same function
`npm run demo`/`demo:bridge` render from Node.

Not published; `private: true`. See [`JAVASCRIPT_API.md`](../../JAVASCRIPT_API.md) §13.

## Setup

```bash
# from the repo root
sbtn "scalajs-jfx-bridge/fastLinkJS"    # or fullLinkJS for the production build
cd npm/scalajs-jfx && npm install       # only needed once
cd ../jfx && npm install && npm run build
cd ../jfx-demo && npm install
```

## Run

```bash
npm run dev              # http://localhost:5174, Vite dev middleware + Express SSR
npm run build && npm start   # production build, served from dist/
```

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
- No real router, no i18n, no document head management -- `src/routes.ts`
  picks `/` vs. `/library` by a plain path string, not a Router component
  (`jfx-router` isn't wired into the bridge yet), and both are fragments
  mounted into a hand-written `index.html`, not a full JFX3 document the way
  `AppDocument` renders one in the Scala app. See JAVASCRIPT_API.md §6 for
  what a full document boundary would need.
