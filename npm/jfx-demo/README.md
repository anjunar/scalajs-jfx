# jfx-demo

The private documentation site and consumer application for the `@anjunar/jfx-*` npm family. It runs the public package exports through Vite, Express SSR, browser hydration, routing, and Node-based smoke runners.

## Overview

The demo is not a published library package. Each page is a real consumer of the npm packages. Core-only pages can use the test stub; feature pages use the linked Scala.js bridge so runtime integration, SSR, hydration, and package boundaries are exercised together.

## Setup

From the repository root:

```bash
sbt --server "scalajs-jfx-bridge/fullLinkJS"
npm install
```

## Run

```bash
npm run dev --workspace npm/jfx-demo       # http://localhost:5174
npm run build --workspace npm/jfx-demo
npm run start --workspace npm/jfx-demo
npm run demo --workspace npm/jfx-demo       # core pages with the stub
npm run demo:bridge --workspace npm/jfx-demo # linked Scala.js runtime
```

## Add a page

Create `src/pages/<name>/page.ts` for the running body and `doc.ts` for its documentation. Page bodies should import only public `@anjunar/jfx-*` packages. Add the page to `src/app/catalog.ts` and, for Node-rendered pages, to `src/app/page-manifest.ts`.

The catalog owns route, navigation, and search metadata. `routes.ts` turns the catalog into the route table. `entry-client.ts` hydrates and `entry-server.ts` renders the same application shape.

## SSR and hydration

The server uses `renderToString` through the bridge and Express serves the result. The browser uses `hydrate` against that result. Client-side navigation is performed by `@anjunar/jfx-router`; the Express server renders the initial request and serves assets.

## Verification

```bash
npm run verify --workspace npm/jfx-demo
```

This runs TypeScript checks, client and server builds, the single-runtime proof, and the page SSR proof.

## Related modules

- [`npm/README.md`](../README.md) explains the TypeScript package architecture.
- [`@anjunar/jfx-core`](../jfx-core/README.md) and [`@anjunar/scalajs-jfx-bridge`](../scalajs-jfx-bridge/README.md) are the runtime boundary used by the demo.
