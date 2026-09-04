# @anjunar/scalajs-jfx-bridge

The Scala.js implementation of [`@anjunar/jfx-core`](../jfx-core/README.md)'s contract --
`AbstractComponent`, `Cursor` and `ReadOnlyProperty` projected to plain
JavaScript, one class per handle, no `@JSExportAll`. See
[`JAVASCRIPT_API.md`](../../JAVASCRIPT_API.md) for why this package exists and
what it does and does not cover yet.

This package has no source of its own: `dist/fullopt/` is the linker's output
for the `jfx-bridge` sbt module (`jfx.bridge`), built with

```bash
sbtn "scalajs-jfx-bridge/fullLinkJS"   # dist/fullopt/main.js -- optimised and minified
```

`package.json`'s `main`/`exports` point at `fullopt`, not `fastopt`: this is a
one-module artifact (`jfx-core` only, §3), and `fullLinkJS` links it in about the
same time as `fastLinkJS` does -- roughly a second, measured. There is no
meaningful dev/prod split to preserve here, so there is only one command to run,
and every consumer -- the demo, the test harness, a real install -- gets the
optimised bundle. Compare `application/`'s own `fastLinkJS`/`fullLinkJS` split,
which exists because that build pulls in the whole component library and the
difference in link time is real (JAVASCRIPT_API.md §14).

`dist/` is gitignored; it is generated, not checked in. `types/` is not -- it is
hand-written and versioned, and it is the only part of this package a human
edits. There is nothing to `npm install` here either: the linked bundle imports
nothing from npm, only JavaScript globals (`window`, `document`) through
`org.scalajs.dom`'s facades.

## Usage

```ts
import { installRuntime } from "@anjunar/jfx-core";
import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge";

installRuntime(bridgeRuntime);
```

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
