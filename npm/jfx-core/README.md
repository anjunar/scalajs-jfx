# @anjunar/jfx-core

The declarative TypeScript API for JFX3 -- the core of it.

This package is **types and ergonomics, not a framework**. Rendering, hydration,
state propagation, routing, forms and every component live in the Scala.js
runtime published as `com.anjunar::scalajs-jfx-bridge`. What you get here is the
boundary of that runtime expressed in TypeScript, and a DSL that reads like the
Scala one.

It is the first package of a family that mirrors the sbt modules: `-router`,
`-viewport`, `-controls` and `-forms` follow as `jfx-bridge` grows to serve
them. What does *not* follow is a second runtime -- every package in the family
shares the one linked Scala.js artifact, as a `peerDependency`. See
[`JAVASCRIPT_API.md` §15](../../JAVASCRIPT_API.md) for the module graph and the
measurements behind that decision.

```bash
npm install @anjunar/jfx-core @anjunar/scalajs-jfx-bridge @anjunar/scalajs-jfx @anjunar/ui
```

## The shape of a page

```ts
import { button, classes, div, onClick, property, text, vbox } from "@anjunar/jfx-core";

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
import { hydrate, installRuntime, mount, renderToString } from "@anjunar/jfx-core";
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

The runnable version of all three paths lives in `npm/jfx-demo`, which consumes
this package the way a stranger would -- through its package exports, not by
reaching into the neighbouring directory. `npm run demo` there renders against
the stub, `npm run demo:bridge` against the real runtime (link it first with
`sbtn "scalajs-jfx-bridge/fastLinkJS"` from the repo root), and `npm run dev`
serves the same pages over Vite and Express with SSR and hydration.

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

`@anjunar/jfx-core/stub` implements the same contract on a small host abstraction, for
unit tests and for developing this package without an sbt build. It reconciles
lists by re-rendering, does not hydrate, and knows nothing about i18n, routing or
forms. It is a test double, not a second implementation.

## Tests

```bash
npm run verify   # typecheck + unit tests + the consumer test
```

`npm test` runs the unit suite against the stub, plus one smoke test against the
really linked bridge -- `sbtn "scalajs-jfx-bridge/fastLinkJS"` has to have run,
and the test says so loudly rather than skipping if it has not.

`npm run test:consumer` is the one that matters for packaging: it runs `npm pack`
on this package and on the bridge, installs both into an empty directory, and
reaches them only through their public `exports`. Every packaging fault this
prototype has had was invisible to a test that imported by relative path, so
this is the test that is allowed to be slow.

## Versioning

The npm major matches the Maven major, exactly as `@anjunar/scalajs-jfx` does.
The three artifacts are released together.

One drift to be aware of until the next release: `@anjunar/scalajs-jfx` is
published as `1.1.0`, while this repository carries it at `3.0.0`. The
`peerDependency` here names `^3.0.0` -- the version the rule asks for, not the
one currently on the registry.
