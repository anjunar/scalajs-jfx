# JFX3 TypeScript packages

The `npm/` workspace contains the TypeScript API for JFX3. These packages provide typed, idiomatic entry points for applications written in TypeScript while the Scala.js JFX runtime remains responsible for rendering, hydration, component lifecycle, and library behavior.

## Architecture

```text
Application
    ↓
@anjunar/jfx-* TypeScript API
    ↓
@anjunar/scalajs-jfx-bridge
    ↓
Scala.js JFX runtime
    ↓
DOM / SSR / hydration
```

`@anjunar/jfx-core` owns the shared TypeScript contract, ambient-scope DSL, properties, and runtime entry points. Feature packages only describe their options and convert them into registered Scala.js components. Install one bridge runtime per process; loading a second runtime is rejected because it would split the component tree.

## Installation

Install the packages used by the application. Most component packages also need the CSS package:

```bash
npm install @anjunar/jfx-core @anjunar/scalajs-jfx-bridge @anjunar/scalajs-jfx
npm install @anjunar/jfx-router @anjunar/jfx-viewport @anjunar/jfx-controls @anjunar/jfx-forms @anjunar/jfx-editor @anjunar/jfx-json
```

Import the bridge once from the application entry point:

```ts
import "@anjunar/scalajs-jfx-bridge";
```

The bridge package is generated from the `jfx-bridge` Scala.js module. It must be linked while developing this repository:

```bash
sbtn "scalajs-jfx-bridge/fullLinkJS"
```

## First page

```ts
import { button, div, hydrate, onClick, property, renderToString, text } from "@anjunar/jfx-core";
import "@anjunar/scalajs-jfx-bridge";

const build = (): void => {
  const message = property("Ready");
  div(() => {
    text(message);
    button("Change", {}, () => onClick(() => message.set("Changed")));
  });
};

// Server: const result = await renderToString(build);
// Browser: await hydrate(document.getElementById("root")!, build);
```

Bodies are synchronous. Use `fetchInto` for asynchronous work registered with the render context; SSR waits for it and hydration can adopt the server tree while the request is still pending. Use `capture` only for later callbacks that need a component position; it does not make SSR wait.

## Packages

- [`@anjunar/jfx-core`](jfx-core/README.md) — DOM DSL, reactive state, rendering, head, and i18n.
- [`@anjunar/jfx-router`](jfx-router/README.md) — route definitions, outlets, links, and route failures.
- [`@anjunar/jfx-viewport`](jfx-viewport/README.md) — windows, anchored overlays, and notifications.
- [`@anjunar/jfx-controls`](jfx-controls/README.md) — tabs, carousel, and virtualized collections.
- [`@anjunar/jfx-forms`](jfx-forms/README.md) — model-bound controls and validators.
- [`@anjunar/jfx-editor`](jfx-editor/README.md) — Markdown editor with optional Lexical plugins.
- [`@anjunar/jfx-json`](jfx-json/README.md) — schema and decorator based JSON mapping.
- [`@anjunar/scalajs-jfx-bridge`](scalajs-jfx-bridge/README.md) — the linked Scala.js runtime entry point.
- [`@anjunar/scalajs-jfx`](scalajs-jfx/README.md) — CSS for classes emitted by the Scala.js components.
- [`jfx-demo`](jfx-demo/README.md) — private documentation site and consumer test application.

## SSR and hydration

The server renders the readable initial state. The browser calls `hydrate` to claim that output and attach behavior. The same linked runtime performs both operations; feature packages do not reimplement them. When a module has a non-JavaScript fallback, its module README documents the exact fallback and the behavior added after hydration.

## Verification

Each published facade has its own `verify` script for typechecking, runtime smoke tests, and a packed consumer test where applicable:

```bash
npm run verify --workspace npm/jfx-core
npm run verify --workspace npm/jfx-router
```

The private demo additionally verifies its client build, SSR build, runtime identity, and page output.
