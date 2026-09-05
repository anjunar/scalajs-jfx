# JFX3

JFX3 is a Scala 3 and Scala.js UI library for server-rendered applications. It combines a component DSL, synchronous reactive state, lifecycle-aware rendering, typed forms, routing, controls, and browser integrations in one Scala.js runtime.

## Overview

JFX3 keeps the component tree as the source of truth. The same component code can render HTML on the server, be claimed during browser hydration, and continue with reactive updates and event handling. Component disposal owns subscriptions, event listeners, timers, and other resources created below that component.

The runtime is available directly from Scala or through the TypeScript packages. TypeScript is a typed facade over the Scala.js runtime; it is not a second UI implementation.

```text
Application
    |
    +-- Scala 3 / Scala.js DSL ------------------+
    |                                             |
    +-- @anjunar/jfx-* TypeScript facade ---------+--> Scala.js JFX runtime
                                                  |
                                                  +--> DOM, SSR, hydration
```

## Choose an API

### Scala / Scala.js

Use the Scala modules when the application, model, and server integration are written in Scala. The public packages are published as `com.anjunar` Scala.js artifacts.

### TypeScript / npm

Use the npm packages when the application is written in TypeScript. `@anjunar/jfx-core` contains the TypeScript contract and DSL. `@anjunar/scalajs-jfx-bridge` installs the linked Scala.js runtime that performs rendering, hydration, state propagation, and library component mounting.

## Scala quick start

```scala
import jfx.core.dsl.ClassDsl.classes
import jfx.core.dsl.EventDsl.onClick
import jfx.core.layout.{Button, Div, TextComponent, VBox}
import jfx.core.state.Property

import Button.button
import Div.div
import TextComponent.text
import VBox.vbox

def page(using jfx.core.component.AbstractComponent, jfx.core.render.Cursor): Unit = {
  val count = Property(0)
  vbox {
    classes = Seq("counter")
    div { text(count.map(value => s"Count: $value")) {} }
    button("Increment") { onClick(_ => count.set(count.get + 1)) }
  }
}
```

The runtime entry points are `Runtime.renderToString` or `Runtime.renderToStringAsync` on the server and `Runtime.mount` or `Runtime.hydrate` in the browser. A Scala.js application must enable the Scala.js plugin and use the module dependency shown in the relevant module README.

## TypeScript quick start

```bash
npm install @anjunar/jfx-core @anjunar/scalajs-jfx-bridge @anjunar/scalajs-jfx
```

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

Call `renderToString` on the server, `hydrate` against the resulting document in the browser, or `mount` into an empty element. Rendering bodies are synchronous; asynchronous data belongs in the core `fetchInto` primitive so SSR can wait for it.

## SSR, hydration, and non-JavaScript behavior

SSR produces the initial readable HTML. Hydration claims that tree and adds browser behavior without requiring a second component implementation. Controls and forms should preserve useful reading, links, and fallback controls in the server output; JavaScript adds writing, validation feedback, navigation, virtualization, and richer interaction where the module supports it.

## Modules

| Area | Scala module | TypeScript package | Responsibility |
| --- | --- | --- | --- |
| Core | [`jfx-core`](jfx-core/README.md) | [`@anjunar/jfx-core`](npm/jfx-core/README.md) | Components, DSL, state, rendering, document head, i18n |
| Routing | [`jfx-router`](jfx-router/README.md) | [`@anjunar/jfx-router`](npm/jfx-router/README.md) | Routes, nested outlets, links, SSR status |
| Viewport | [`jfx-viewport`](jfx-viewport/README.md) | [`@anjunar/jfx-viewport`](npm/jfx-viewport/README.md) | Windows, overlays, notifications |
| Controls | [`jfx-controls`](jfx-controls/README.md) | [`@anjunar/jfx-controls`](npm/jfx-controls/README.md) | Tabs, carousel, table, data grid, virtual list |
| Forms | [`jfx-forms`](jfx-forms/README.md) | [`@anjunar/jfx-forms`](npm/jfx-forms/README.md) | Model binding, validation, nested forms, media |
| Editor | [`jfx-editor`](jfx-editor/README.md) | [`@anjunar/jfx-editor`](npm/jfx-editor/README.md) | Markdown editor backed by Lexical |
| JSON | [`jfx-json`](jfx-json/README.md) | [`@anjunar/jfx-json`](npm/jfx-json/README.md) | Explicit schema-based JSON mapping |
| WebAuthn | [`jfx-webAuthn`](jfx-webAuthn/README.md) | — | Browser WebAuthn and passkey ceremonies |
| Bridge | [`jfx-bridge`](jfx-bridge/README.md) | [`@anjunar/scalajs-jfx-bridge`](npm/scalajs-jfx-bridge/README.md) | JavaScript runtime boundary and linked bundle |
| CSS | — | [`@anjunar/scalajs-jfx`](npm/scalajs-jfx/README.md) | Default styles for JFX-rendered classes |

The runnable examples are in [`application`](application) for Scala and [`npm/jfx-demo`](npm/jfx-demo) for TypeScript. The demo is a consumer and is not a library module.

## Build and tests

Use `sbtn`, not `sbt`:

```bash
sbtn "Test/testOnly *"
```

This runs the complete Scala test suite. For the npm packages, link the bridge first and then run the package's verification command:

```bash
sbtn "scalajs-jfx-bridge/fullLinkJS"
npm run verify --workspace npm/jfx-core
```

The repository is currently versioned as `3.0.0-SNAPSHOT` and licensed under MIT.

## Related documentation

- The `jfx-controls` module README explains the virtualized collection model.
- [`npm/README.md`](npm/README.md) explains the TypeScript package family in more detail.
