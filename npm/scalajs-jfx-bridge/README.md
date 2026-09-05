# @anjunar/scalajs-jfx-bridge

The Scala.js implementation of [`@anjunar/jfx-core`](../jfx-core/README.md)'s contract --
`AbstractComponent`, `Cursor` and `ReadOnlyProperty` projected to plain
JavaScript, one class per handle, no `@JSExportAll`. See
[`JAVASCRIPT_API.md`](../../JAVASCRIPT_API.md) for why this package exists and
what it does and does not cover yet.

The handwritten `index.js` installs the runtime into `@anjunar/jfx-core` and
re-exports `bridgeRuntime`. `dist/fullopt/` is the linker's output for the
`jfx-bridge` sbt module (`jfx.bridge`), built with

```bash
sbtn "scalajs-jfx-bridge/fullLinkJS"   # dist/fullopt/main.js -- optimised and minified
```

`package.json`'s `main`/`exports` point at `index.js`, which imports the
optimised `fullopt` bundle. Every consumer -- the demo, the test harness and
a packed install -- uses this same entry point.

`dist/` is gitignored; it is generated, not checked in. `types/` is not -- it is
hand-written and versioned, as is `index.js`. The core peer dependency provides
the shared runtime slot; the linked editor uses `@anjunar/scalajs-lexical`.

## Usage

```ts
import "@anjunar/scalajs-jfx-bridge";
```

This import automatically installs the runtime before the importing module's
body runs. Named imports of `bridgeRuntime` have the same effect. A module that
creates properties at module load must import the bridge itself; sibling imports
can evaluate concurrently when a dependency initializes asynchronously.
The package declares `sideEffects: true` so bundlers retain the installation.
Core's existing guard still rejects a different runtime in the same slot.
Tests can continue to use `installRuntime` explicitly; after `resetRuntime`,
reinstall explicitly because an already evaluated import is cached.

The types live in [`types/index.d.ts`](types/index.d.ts), and they *import*
`JfxRuntime` from `@anjunar/jfx-core` rather than restating it -- the contract
keeps exactly one home (`npm/jfx-core/src/contract.ts`) instead of two copies
that can drift apart. The dependency direction matches the Scala side exactly:
`jfxBridge.dependsOn(jfxCore)` (ARCHITECTURE.md §1), so `@anjunar/jfx-core` is a
`peerDependency` here.

This replaced an ambient `declare module` that used to live in `@anjunar/jfx-core`
itself. That arrangement never reached a consumer: `tsc` does not copy an input
`.d.ts` into `outDir`, and it strips the `/// <reference path>` meant to pull it
into the consumer's program. Anyone installing both packages got TS7016 under
`strict`. `npm/jfx-core/test/consumer/` now typechecks a packed install with
`skipLibCheck: false`, so the regression cannot come back quietly.

## What's registered

`ScopeHandle.component(name, ...)` resolves against a small registry, filled
in at module load by [`jfx-bridge/.../BridgeRuntime.scala`](../../jfx-bridge/src/main/scala-3/jfx/bridge/BridgeRuntime.scala):
`vbox`, `hbox`, `button` today -- everything `jfx-core` already has under
`jfx.core.layout`. `jfx-controls` is not linked in yet (JAVASCRIPT_API.md §9,
step 6).
