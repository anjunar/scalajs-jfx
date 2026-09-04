# @anjunar/jfx

The declarative TypeScript API for JFX3.

This package is **types and ergonomics, not a framework**. Rendering, hydration,
state propagation, routing, forms and every component live in the Scala.js
runtime published as `com.anjunar::scalajs-jfx-bridge`. What you get here is the
boundary of that runtime expressed in TypeScript, and a DSL that reads like the
Scala one.

```bash
npm install @anjunar/jfx @anjunar/scalajs-jfx-bridge @anjunar/scalajs-jfx @anjunar/ui
```

## The shape of a page

```ts
import { button, classes, div, onClick, property, text, vbox } from "@anjunar/jfx";

export function statePage(): void {
  const counter = property(0);
  const status = counter.map((value) => `Current value: ${value}`);

  vbox(() => {
    classes("clarity-grid");

    div(() => {
      classes("docs-card");
      div(() => { classes("docs-card__title"); text(status); });
    });

    button("Increment", {}, () => {
      classes("calm-action", "calm-action--primary");
      onClick(() => counter.set(counter.get + 1));
    });
  });
}
```

Side by side with the Scala original, the nesting, the order and the lifecycle
are the same. Scala passes the parent and the cursor through
`(using AbstractComponent, Cursor)`; TypeScript has no implicit parameters, so
the same pair travels in an ambient scope that the builders push and pop.

## Booting

```ts
import { hydrate, installRuntime, mount, renderToString } from "@anjunar/jfx";
import { bridgeRuntime } from "@anjunar/scalajs-jfx-bridge";

installRuntime(bridgeRuntime);

// browser, claiming the server-rendered tree
await hydrate(document, () => statePage());

// browser, empty container
mount(document.querySelector("#root")!, () => statePage());

// node
const { html, status } = await renderToString(() => statePage());
```

`installRuntime` is called once per process. It is the only module-level state in
this package, and it is constant after boot.

`npm run demo:bridge` runs exactly the code above's `renderToString` path
against `bridgeRuntime` -- `demo/pages.ts`'s `statePage`/`libraryPage`, the same
functions `npm run demo` renders against the stub. It needs
`@anjunar/scalajs-jfx-bridge` linked first: `sbtn "scalajs-jfx-bridge/fastLinkJS"`
from the repo root, then `npm install` here.

## The one rule

**A render body is synchronous.** Nothing awaits while a scope is installed.

That is what makes an ambient scope safe in a server process shared by many
requests: the stack is non-empty only during synchronous execution, and
JavaScript never interleaves synchronous execution, so a second request cannot
observe the first one's scope. Break the rule and you get an error, not a
mystery: a body that returns a promise is refused where it is written, and an
escaped continuation is refused when it tries to compose.

Asynchronous data comes in through `fetchInto`, which registers the promise with
the render's async context, so server rendering waits for it and hydration
tolerates it still being in flight:

```ts
fetchInto(loadBooks, (books) => {
  forEach(books, (book) => div(() => text(book.title)));
});
```

For a deferral you write yourself -- a `setTimeout`, a callback from a third
party -- `capture()` freezes the current position so the callback can compose
into it:

```ts
const restore = capture();
setTimeout(() => restore(() => text("late")), 100);
```

`capture()` restores the position; it does **not** make server rendering wait.
Use `fetchInto` when the output must contain the result.

## Styling

Unchanged from the Scala side: `@anjunar/ui` owns the design tokens,
`@anjunar/scalajs-jfx` owns every `.jfx-*` class a published module renders, and
your application owns its own class names. See that package's README for the
boundaries.

## The stub runtime

`@anjunar/jfx/stub` implements the same contract on a small host abstraction, for
unit tests and for developing this package without an sbt build. It reconciles
lists by re-rendering, does not hydrate, and knows nothing about i18n, routing or
forms. It is a test double, not a second implementation.

## Versioning

The npm major matches the Maven major, exactly as `@anjunar/scalajs-jfx` does.
The three artifacts are released together.
