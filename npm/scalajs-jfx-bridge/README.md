# @anjunar/scalajs-jfx-bridge

The linked Scala.js runtime for the JFX3 TypeScript API. Import this package once at application startup to install the runtime used by `@anjunar/jfx-core` and the feature packages.

## Overview

This package is the JavaScript boundary of the Scala JFX implementation. It contains no second renderer or component implementation: `jfx-bridge` links the Scala.js modules and registers their factories, while the npm packages provide typed wrappers around those factories.

```text
TypeScript application
        ↓
@anjunar/jfx-* facades
        ↓
@anjunar/scalajs-jfx-bridge
        ↓
Scala.js JFX runtime
```

## Installation

```bash
npm install @anjunar/jfx-core @anjunar/scalajs-jfx-bridge @anjunar/scalajs-jfx
```

The package is published with hand-written declarations in `types/index.d.ts`. The generated `dist/` bundle is produced by Scala.js and is not edited manually.

## Quick start

```ts
import { div, mount, text } from "@anjunar/jfx-core";
import "@anjunar/scalajs-jfx-bridge";

mount(document.getElementById("root")!, () => {
  div(() => text("Mounted by the Scala.js JFX runtime."));
});
```

The side-effect import installs `bridgeRuntime` automatically. `bridgeRuntime` is exported as a typed `JfxRuntime` for integrations that need to inspect the installed runtime.

## Runtime boundary

The bridge projects Scala values to JavaScript-safe shapes: arrays instead of Scala collections, `null` instead of `Option`, promises at asynchronous boundaries, and opaque handles for components and properties. `mount`, `hydrate`, `renderToString`, `property`, and `listProperty` are exposed through `@anjunar/jfx-core`.

The package must be paired with matching versions of `@anjunar/jfx-core` and `@anjunar/scalajs-jfx`. A second, different runtime in the same process is rejected because it would split the component tree.

## API overview

- `bridgeRuntime` — the linked `JfxRuntime` instance.
- `JfxRuntime` — the shared contract implemented by the linked bundle.
- Component and property handles — opaque projections used by the core facade.
- Registered factories — the Scala implementations behind router, controls, viewport, forms, and editor facades.

## Development

From the repository root:

```bash
sbtn "scalajs-jfx-bridge/fullLinkJS"
npm run verify --workspace npm/jfx-core
```

The linker output is generated under `npm/scalajs-jfx-bridge/dist/`.
