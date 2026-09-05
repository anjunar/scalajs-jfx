# scalajs-jfx-core

The core Scala.js runtime and DSL for JFX3. Use it to compose DOM components, bind reactive state, render on the server, hydrate in the browser, and manage component-owned resources.

## Overview

`jfx-core` is the foundation for every other JFX module. It provides the component lifecycle, render cursors, state primitives, HTML layout components, control flow, async render registration, document head, request context, and i18n primitives.

## Installation

```scala
libraryDependencies += "com.anjunar" %% "scalajs-jfx-core" % "3.0.0-SNAPSHOT"
```

Enable the Scala.js sbt plugin in the consuming project. In this repository the module is built with Scala 3.3.8 and sbt 2; `%%` supplies the Scala.js platform suffix in this build.

## Quick start

```scala
import jfx.core.dsl.ClassDsl.classes
import jfx.core.dsl.EventDsl.onClick
import jfx.core.layout.{Button, Div, TextComponent, VBox}
import jfx.core.state.Property

import Button.button
import Div.div
import TextComponent.text
import VBox.vbox

def counter(using jfx.core.component.AbstractComponent, jfx.core.render.Cursor): Unit = {
  val value = Property(0)
  vbox {
    classes = Seq("counter")
    div { text(value.map(number => s"Count: $number")) {} }
    button("Increment") { onClick(_ => value.set(value.get + 1)) }
  }
}
```

## Core concepts

`DslLayer.child` mounts a child and unmounts a partially built child if composition fails. Disposables registered on a component are released when that component leaves the tree. The DSL passes the current `AbstractComponent` and `Cursor` as Scala 3 contextual values.

`Property[T]` exposes `get`, `set`, `setAlways`, `reset`, `isDirty`, `observe`, and `observeWithoutInitial`. `ListProperty[T]` is a reactive list and a `ListDataSource`; structural changes can drive `Foreach` or virtualized controls. Property notifications are synchronous and propagation cycles are rejected.

`Condition.when` mounts its body while a boolean property is true. `Foreach` mounts one body per list item. `FetchComponent.fetch` registers asynchronous work with the render context so SSR can wait for it.

`Runtime.renderToString` renders a fragment and `Runtime.renderToStringAsync` waits for async work. `Runtime.mount` renders into an empty host; `Runtime.hydrate` claims server-rendered nodes. `Head.head`, `DocumentHead`, and `jfx.core.i18n` provide document metadata and locale-aware messages.

## SSR and hydration

Core owns the rendering contract. SSR is readable without JavaScript; hydration adds reactive writes and event behavior. Use the same component body for both paths so the browser can claim the server structure.

## API overview

- `jfx.core.component.Runtime` — mount, unmount, render, and async render entry points.
- `jfx.core.state.Property` / `ListProperty` — reactive scalar and collection state.
- `jfx.core.layout` — `div`, `span`, `button`, `vbox`, `hbox`, `head`, and related components.
- `jfx.core.dsl` — classes, attributes, styles, properties, and events.
- `Condition`, `FetchComponent`, and `Foreach` — dynamic composition.
- `DocumentHead` and `jfx.core.i18n` — head entries, catalogs, and interpolation.

## Related modules

- [`jfx-router`](../jfx-router/README.md) adds route matching and navigation.
- [`jfx-forms`](../jfx-forms/README.md) binds controls to model properties.
- [`jfx-controls`](../jfx-controls/README.md) adds higher-level collections and panels.
- [`jfx-viewport`](../jfx-viewport/README.md) adds the global UI layer.
