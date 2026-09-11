# UI 3 TypeScript packages

The `npm/` workspace contains the TypeScript API for UI 3. These packages provide typed, idiomatic entry points for applications written in TypeScript while the Scala.js UI runtime remains responsible for rendering, hydration, component lifecycle, and library behavior.

## Architecture

```text
Application
    ↓
@anjunar/scalajs-ui-* TypeScript API
    ↓
@anjunar/scalajs-ui-bridge
    ↓
Scala.js UI runtime
    ↓
DOM / SSR / hydration
```

`@anjunar/scalajs-ui-core` owns the shared TypeScript contract, ambient-scope DSL, properties, and runtime entry points. Feature packages only describe their options and convert them into registered Scala.js components. Install one bridge runtime per process; loading a second runtime is rejected because it would split the component tree.

## Installation

Install the packages used by the application. Most component packages also need the CSS package:

```bash
npm install @anjunar/scalajs-ui-core @anjunar/scalajs-ui-bridge @anjunar/scalajs-ui
npm install @anjunar/scalajs-ui-router @anjunar/scalajs-ui-viewport @anjunar/scalajs-ui-controls @anjunar/scalajs-ui-forms @anjunar/scalajs-ui-editor @anjunar/scalajs-ui-json @anjunar/scalajs-ui-webauthn
```

Import the bridge once from the application entry point:

```ts
import "@anjunar/scalajs-ui-bridge";
```

The bridge package is generated from the `scalajs-ui-bridge` Scala.js module. It must be linked while developing this repository:

```bash
sbt --server "scalajs-ui-bridge/fullLinkJS"
```

## First page

```ts
import { button, div, hydrate, onClick, property, renderToString, text } from "@anjunar/scalajs-ui-core";
import "@anjunar/scalajs-ui-bridge";

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

- [`@anjunar/scalajs-ui-core`](scalajs-ui-core/README.md) — DOM DSL, reactive state, rendering, head, and i18n.
- [`@anjunar/scalajs-ui-router`](scalajs-ui-router/README.md) — route definitions, outlets, links, and route failures.
- [`@anjunar/scalajs-ui-viewport`](scalajs-ui-viewport/README.md) — windows, anchored overlays, and notifications.
- [`@anjunar/scalajs-ui-controls`](scalajs-ui-controls/README.md) — tabs, carousel, and virtualized collections.
- [`@anjunar/scalajs-ui-forms`](scalajs-ui-forms/README.md) — model-bound controls and validators.
- [`@anjunar/scalajs-ui-editor`](scalajs-ui-editor/README.md) — Markdown editor with optional Lexical plugins.
- [`@anjunar/scalajs-ui-json`](scalajs-ui-json/README.md) — schema and decorator based JSON mapping.
- [`@anjunar/scalajs-ui-webauthn`](scalajs-ui-webauthn/README.md) — browser WebAuthn and passkey ceremonies.
- [`@anjunar/scalajs-ui-bridge`](scalajs-ui-bridge/README.md) — the linked Scala.js runtime entry point.
- [`@anjunar/scalajs-ui`](scalajs-ui/README.md) — CSS for classes emitted by the Scala.js components.
- [`scalajs-ui-demo`](scalajs-ui-demo/README.md) — private documentation site and consumer test application.

## SSR and hydration

The server renders the readable initial state. The browser calls `hydrate` to claim that output and attach behavior. The same linked runtime performs both operations; feature packages do not reimplement them. When a module has a non-JavaScript fallback, its module README documents the exact fallback and the behavior added after hydration.

## Verification

Each published facade has its own `verify` script for typechecking, runtime smoke tests, and a packed consumer test where applicable:

```bash
npm run verify --workspace npm/scalajs-ui-core
npm run verify --workspace npm/scalajs-ui-router
```

The private demo additionally verifies its client build, SSR build, runtime identity, and page output.
