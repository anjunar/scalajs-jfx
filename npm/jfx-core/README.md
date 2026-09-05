# @anjunar/jfx-core

The declarative TypeScript API for JFX 3. It provides the shared contract, ambient-scope DSL, reactive properties, rendering entry points, document head, and i18n helpers; the linked Scala.js runtime performs the actual rendering.

## Overview

This package is a typed facade, not an independent framework. `@anjunar/scalajs-jfx-bridge` supplies the production runtime. The separate `@anjunar/jfx-core/stub` export is a test double for the core DSL and does not implement the Scala.js renderer.

## Installation

```bash
npm install @anjunar/jfx-core @anjunar/scalajs-jfx-bridge @anjunar/scalajs-jfx @anjunar/ui
```

Import the bridge before rendering:

```ts
import "@anjunar/scalajs-jfx-bridge";
```

## Quick start

```ts
import { button, div, onClick, property, text, vbox } from "@anjunar/jfx-core";
import "@anjunar/scalajs-jfx-bridge";

export function page(): void {
  const count = property(0);
  vbox(() => {
    div(() => text(count.map((value) => `Count: ${value}`)));
    button("Increment", {}, () => onClick(() => count.set(count.get + 1)));
  });
}
```

## Booting and rendering

```ts
import { hydrate, mount, renderToString } from "@anjunar/jfx-core";

const build = (): void => page();

const result = await renderToString(build); // server
await hydrate(document.getElementById("root")!, build); // browser, SSR output
mount(document.getElementById("empty-root")!, build); // browser, empty host
```

`renderToString` returns `{ html, status, headers }`. Use `document: true` when the body composes a complete document with `head()` and `body` roots. Rendering bodies are synchronous. Use `fetchInto` for async work that SSR must await; use `capture` for a later callback that needs to resume a component scope. Capturing a scope does not make SSR wait.

## Core concepts

- `property` creates a synchronous `Property`; `listProperty` creates a reactive list.
- Element builders include `div`, `span`, `section`, `article`, `paragraph`, `nav`, `ul`, `li`, `pre`, `code`, `anchor`, and `heading`.
- `button`, `vbox`, and `hbox` are registered library components.
- `classes`, `attr`, `style`, `on`, `onClick`, and `onDoubleClick` configure the current component.
- `when`, `forEach`, and `fetchInto` compose dynamic content with lifecycle ownership.
- `head`, `documentHead`, `i18nProvider`, `i18n`, `i18nc`, and `t` cover metadata and translations.

## SSR and hydration

SSR creates readable HTML. Hydration claims the same tree and adds event handlers and reactive writes. The ambient scope is valid only while a synchronous body is composing; escaped callbacks must use `capture` or a component-owned async primitive.

## API overview

- Runtime: `mount`, `hydrate`, `renderToString`, `installRuntime`, `runtime`.
- State: `property`, `listProperty`, `Property`, `ListProperty`, `ReadOnlyProperty`.
- DSL: element builders, `component`, `button`, `classes`, `attr`, `style`, `onClick`.
- Scope: `capture`, `currentComponent`, `currentScope`, `withScope`.
- Document and i18n: `head`, `documentHead`, `title`, `meta`, `link`, `i18nProvider`, `i18n`.

## Related modules

- [`@anjunar/jfx-router`](../jfx-router/README.md) adds navigation.
- [`@anjunar/jfx-forms`](../jfx-forms/README.md) adds model-bound controls.
- [`@anjunar/jfx-controls`](../jfx-controls/README.md) adds collections and panels.
