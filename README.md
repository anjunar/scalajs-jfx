# UI 3

UI 3 is a Scala 3 and Scala.js UI library for server-rendered applications. It combines a component DSL, synchronous reactive state, lifecycle-aware rendering, typed forms, routing, controls, and browser integrations in one Scala.js runtime.

## Overview

UI 3 keeps the component tree as the source of truth. The same component code can render HTML on the server, be claimed during browser hydration, and continue with reactive updates and event handling. Component disposal owns subscriptions, event listeners, timers, and other resources created below that component.

The runtime is available directly from Scala or through the TypeScript packages. TypeScript is a typed facade over the Scala.js runtime; it is not a second UI implementation.

```text
Application
    |
    +-- Scala 3 / Scala.js DSL ------------------+
    |                                             |
    +-- @anjunar/scalajs-ui-* TypeScript facade ---------+--> Scala.js UI runtime
                                                  |
                                                  +--> DOM, SSR, hydration
```

The landing page and both demos offer four designs with independent light/dark selection. See [Multi Design](MULTI_DESIGN.md) for the shared packages, preview URLs and verification steps.

## Landing page

The product page is the private npm workspace [scalajs-ui-landing](npm/scalajs-ui-landing/README.md), with English/German SSR, shared designs and a live UI example loaded on demand. Use `npm run build:landing` and `npm run preview:landing` for the standalone page. `npm run check:pages` validates the full site in `dist/pages`; `npm run preview:pages` serves it with both demos.

## Choose an API

To run this repository’s Scala.js demo locally, use `npm run dev` from the repository root, then open `http://localhost:3000/scalajs-ui/`. The `predev` step links the demo with `sbt --server "scalajs-ui-demo/fastLinkJS"` before Vite loads its SSR module and sourcemap. After changing Scala sources, run that link command again (or keep it running with sbt’s `~` watch prefix).

### Scala / Scala.js

Use the Scala modules when the application, model, and server integration are written in Scala. The public packages are published as `com.anjunar` Scala.js artifacts.

### TypeScript / npm

Use the npm packages when the application is written in TypeScript. `@anjunar/scalajs-ui-core` contains the TypeScript contract and DSL. `@anjunar/scalajs-ui-bridge` installs the linked Scala.js runtime that performs rendering, hydration, state propagation, and library component mounting.

## Quick start

### Scala / Scala.js

Enable Scala.js in `project/plugins.sbt`:

```scala
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.22.0")
```

Add UI 3 in `build.sbt` (sbt 2 uses `%%` for the Scala.js platform suffix):

```scala
enablePlugins(ScalaJSPlugin)
scalaVersion := "3.3.8"
scalaJSUseMainModuleInitializer := true
libraryDependencies += "com.anjunar" %% "scalajs-ui-core" % "1.0.0"
```

Add a host element to `index.html`:

```html
<div id="root"></div>
<script type="module" src="./target/scala-3.3.8/example-fastopt/main.js"></script>
```

Compose and mount the counter:

```scala
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.ClassDsl.classes
import ui.core.dsl.EventDsl.onClick
import ui.core.layout.{Button, Div, TextComponent, VBox}
import ui.core.render.{Cursor, DomCursor}
import ui.core.state.Property
import org.scalajs.dom

import Button.button
import Div.div
import TextComponent.text
import VBox.vbox

final class CounterApp extends AbstractComponent {
  val tagName = "main"

  override def compose(cursor: Cursor): Unit = {
    val count = Property(0)
    vbox {
      classes = Seq("counter")
      div { text(count.map(value => s"Count: $value")) {} }
      button("Increment") { onClick(_ => count.set(count.get + 1)) }
    }
  }
}

object Main {
  def main(args: Array[String]): Unit =
    Runtime.mount(new CounterApp, DomCursor.root(dom.document.getElementById("root")))
}
```

Run `sbt --server fastLinkJS`, then serve the project directory with an HTTP server. For SSR, `Runtime.renderToString` and `Runtime.renderToStringAsync` use an `SsrCursor`. Browser hydration creates a `HydratingCursor.root(...)` and mounts the same component tree through `Runtime.mount`, as demonstrated by [`scala/scalajs-ui-demo/src/main/scala-3/app/Main.scala`](scala/scalajs-ui-demo/src/main/scala-3/app/Main.scala).

### TypeScript / npm

```bash
npm install @anjunar/scalajs-ui-core @anjunar/scalajs-ui-bridge @anjunar/scalajs-ui
```

Create `index.html`:

```html
<div id="root"></div>
<script type="module" src="/src/main.ts"></script>
```

Create `src/main.ts`:

```ts
import { button, div, mount, onClick, property, text, vbox } from "@anjunar/scalajs-ui-core";
import "@anjunar/scalajs-ui-bridge";
import "@anjunar/scalajs-ui/index.css";

function page(): void {
  const count = property(0);
  vbox(() => {
    div(() => text(count.map((value) => `Count: ${value}`)));
    button("Increment", {}, () => onClick(() => count.set(count.get + 1)));
  });
}

mount(document.getElementById("root")!, page);
```

Run it with a TypeScript-aware bundler such as Vite. Call `renderToString` on the server, `hydrate` against the resulting document in the browser, or `mount` into an empty element. Rendering bodies are synchronous; asynchronous data belongs in the core `fetchInto` primitive so SSR can wait for it.

## SSR, hydration, and non-JavaScript behavior

SSR produces the initial readable HTML. Hydration claims that tree and adds browser behavior without requiring a second component implementation. Controls and forms should preserve useful reading, links, and fallback controls in the server output; JavaScript adds writing, validation feedback, navigation, virtualization, and richer interaction where the module supports it.

## Modules

| Area | Scala module | TypeScript package | Responsibility |
| --- | --- | --- | --- |
| Core | [`scalajs-ui-core`](scala/scalajs-ui-core/README.md) | [`@anjunar/scalajs-ui-core`](npm/scalajs-ui-core/README.md) | Components, DSL, state, rendering, document head, i18n |
| Routing | [`scalajs-ui-router`](scala/scalajs-ui-router/README.md) | [`@anjunar/scalajs-ui-router`](npm/scalajs-ui-router/README.md) | Routes, nested outlets, links, SSR status |
| Viewport | [`scalajs-ui-viewport`](scala/scalajs-ui-viewport/README.md) | [`@anjunar/scalajs-ui-viewport`](npm/scalajs-ui-viewport/README.md) | Windows, overlays, notifications |
| Controls | [`scalajs-ui-controls`](scala/scalajs-ui-controls/README.md) | [`@anjunar/scalajs-ui-controls`](npm/scalajs-ui-controls/README.md) | Tabs, carousel, table, data grid, virtual list |
| Forms | [`scalajs-ui-forms`](scala/scalajs-ui-forms/README.md) | [`@anjunar/scalajs-ui-forms`](npm/scalajs-ui-forms/README.md) | Model binding, validation, nested forms, media |
| Editor | [`scalajs-ui-editor`](scala/scalajs-ui-editor/README.md) | [`@anjunar/scalajs-ui-editor`](npm/scalajs-ui-editor/README.md) | Markdown editor backed by Lexical |
| JSON | [`scalajs-ui-json`](scala/scalajs-ui-json/README.md) | [`@anjunar/scalajs-ui-json`](npm/scalajs-ui-json/README.md) | Explicit schema-based JSON mapping |
| WebAuthn | [`scalajs-ui-webauthn`](scala/scalajs-ui-webauthn/README.md) | [`@anjunar/scalajs-ui-webauthn`](npm/scalajs-ui-webauthn/README.md) | Browser WebAuthn and passkey ceremonies |
| Bridge | [`scalajs-ui-bridge`](scala/scalajs-ui-bridge/README.md) | [`@anjunar/scalajs-ui-bridge`](npm/scalajs-ui-bridge/README.md) | JavaScript runtime boundary and linked bundle |
| CSS | — | [`@anjunar/scalajs-ui`](npm/scalajs-ui/README.md) | Default styles for UI-rendered classes |

The runnable examples are in [`scalajs-ui-demo`](scala/scalajs-ui-demo) for Scala and [`npm/scalajs-ui-demo`](npm/scalajs-ui-demo) for TypeScript. The demo is a consumer and is not a library module.

Set the shared project version in the Scala build, npm workspaces, demos, lockfile, and installation examples with one command:

```bash
npm run set-version -- 3.0.1
```

CI can verify the checked-in values without changing files with `npm run check-version -- 3.0.1`.

After the version change and successful release checks, publish Maven Central and all public npm packages with:

```powershell
.\scripts\deploy.ps1
```

## Build and tests

Use sbt 2 through the `sbt` runner:

```bash
sbt --server "Test/testOnly *"
```

This runs the complete Scala test suite. For the npm packages, link the bridge first and then run the package's verification command:

```bash
sbt --server "scalajs-ui-bridge/fullLinkJS"
npm run verify --workspace npm/scalajs-ui-core
```

## Project status and license

The repository is on the `1.0.0` release line and under active development. The complete Scala suite and every npm workspace verification run in CI for pushes and pull requests. Source, releases, and issue tracking live in the [GitHub repository](https://github.com/anjunar/scalajs-ui).

UI 3 is available under the [MIT License](LICENSE).

## Related documentation

- The `scalajs-ui-controls` module README explains the virtualized collection model.
- [`npm/README.md`](npm/README.md) explains the TypeScript package family in more detail.
