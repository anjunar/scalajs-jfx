# @anjunar/scalajs-jfx-bridge

The Scala.js implementation of [`@anjunar/jfx`](../jfx/README.md)'s contract --
`AbstractComponent`, `Cursor` and `ReadOnlyProperty` projected to plain
JavaScript, one class per handle, no `@JSExportAll`. See
[`JAVASCRIPT_API.md`](../../JAVASCRIPT_API.md) for why this package exists and
what it does and does not cover yet.

This package has no source of its own: `dist/` is the linker's output for the
`jfx-bridge` sbt module (`jfx.bridge`), built with

```bash
sbtn "scalajs-jfx-bridge/fastLinkJS"   # dev: dist/fastopt/main.js
sbtn "scalajs-jfx-bridge/fullLinkJS"   # dist/fullopt/main.js -- not yet wired as the published build
```

`dist/` is gitignored; it is generated, not checked in. There is nothing to
`npm install` here either -- the linked bundle imports nothing from npm, only
from JavaScript globals (`window`, `document`) through `org.scalajs.dom`'s
facades.

## Usage

```ts
import { installRuntime } from "@anjunar/jfx";
import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge";

installRuntime(bridgeRuntime);
```

`@anjunar/jfx` declares the shape of `bridgeRuntime` itself, as an ambient
module in [`npm/jfx/src/bridge.d.ts`](../jfx/src/bridge.d.ts) -- this package
ships no `.d.ts` of its own, so that the contract has exactly one home
(`npm/jfx/src/contract.ts`) instead of two copies that can drift apart.

## What's registered

`ScopeHandle.component(name, ...)` resolves against a small registry, filled
in at module load by [`jfx-bridge/.../BridgeRuntime.scala`](../../jfx-bridge/src/main/scala-3/jfx/bridge/BridgeRuntime.scala):
`vbox`, `hbox`, `button` today -- everything `jfx-core` already has under
`jfx.core.layout`. `jfx-controls` is not linked in yet (JAVASCRIPT_API.md §9,
step 6).
