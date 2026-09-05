# scalajs-jfx-bridge

The Scala.js JavaScript boundary for JFX3. This module links the Scala.js runtime used by the TypeScript packages and exposes opaque handles for rendering, state, components, forms, routing, controls, viewport features, and i18n.

## Overview

The bridge is an integration module rather than a second UI framework. It depends on the Scala JFX modules and registers their component factories under the names consumed by the npm facades. The npm package [`@anjunar/scalajs-jfx-bridge`](../npm/scalajs-jfx-bridge/README.md) packages the linked output and installs one `JfxRuntime` into `@anjunar/jfx-core`.

## Building the linked runtime

```bash
sbtn "scalajs-jfx-bridge/fullLinkJS"
```

The output is written to `npm/scalajs-jfx-bridge/dist/fullopt` and is generated; it is not a source file to edit. The checked-in bridge entry point and TypeScript declarations live in the npm package.

## Runtime boundary

The bridge projects Scala values to JavaScript-safe shapes: arrays instead of Scala collections, `null` instead of `Option`, promises at async boundaries, and opaque handles for components and properties. `JfxRuntime` provides `property`, `listProperty`, `mount`, `hydrate`, and `renderToString`. Component factories translate typed npm options into the corresponding Scala component constructors.

## Registered feature factories

The linked runtime registers core elements and the feature entries used by the npm packages: routing, controls, viewport, forms, editor, and i18n. The facade packages own TypeScript option types; the Scala bridge owns the conversion and the actual component behavior.

## API overview

- `JfxRuntimeBridge` — runtime implementation used by the npm entry point.
- `PropertyHandle`, `ListPropertyHandle`, `ReadOnlyPropertyHandle` — reactive handles.
- `ScopeHandleBridge` and `ComponentHandleBridge` — scoped composition and component operations.
- `SsrResultHandle` — HTML, status, and headers returned by SSR.
- feature factory objects — registration for router, controls, viewport, forms, editor, and i18n.

## Related modules

- [`jfx-core`](../jfx-core/README.md) owns the shared component and state contracts.
- [`npm/README.md`](../npm/README.md) documents the TypeScript architecture.
- [`npm/scalajs-jfx-bridge`](../npm/scalajs-jfx-bridge/README.md) is the consumer-facing package.
