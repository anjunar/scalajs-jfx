# scala-js-jfx

`scala-js-jfx` is a reactive UI framework for Scala.js with a strong focus on structured state, lifecycle control, and composable UI DSLs.

It provides a declarative, JavaFX-inspired DSL for building DOM trees, managing reactive state, wiring forms to model properties, and rendering route-driven views in the browser.

## Why This Library?

Most Scala.js UI frameworks focus either on functional reactivity or simple DOM composition. They often lack:

- explicit lifecycle control
- structured component boundaries
- safe resource disposal
- scalable UI composition for complex applications

`scala-js-jfx` addresses these gaps by combining:

- a `NodeScope` / `Dispose` model for precise lifecycle management
- a browser-focused DSL inspired by JavaFX
- reactive state bindings without hidden magic
- strong support for virtualized and dynamic UIs

This makes it especially suitable for larger frontend applications, not just small demos.

The library is organized around a few core ideas:

- DOM components written in plain Scala
- reactive values with `Property` and `ListProperty`
- scoped dependency injection with `Scope`
- form binding and validation based on model structure
- asynchronous routing for browser-driven navigation

## Core Concepts

### NodeScope And Lifecycle

Every UI subtree lives in a controlled scope. That gives you:

- deterministic cleanup
- safer resource handling
- safe reuse in virtualized components

### Reactive State

State is explicit and observable:

- no hidden reactivity layers
- predictable updates
- composable bindings

### Layout DSL

UI is declared with composable building blocks instead of ad-hoc DOM wiring:

```scala
vbox(
  hbox(
    button("Click"),
    label("Status")
  )
)
```

### Forms And Binding

The framework includes built-in support for:

- bidirectional binding
- validation
- structured form models

## Module

This repository contains multiple modules, but the reusable library is `jfx`.

```scala
lazy val jfx = (project in file("jfx"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "scala-js-jfx",
    moduleName := "scala-js-jfx"
  )
```

## Installation

Add the library to a Scala.js project:

```scala
libraryDependencies += "com.anjunar" %%% "scala-js-jfx" % "1.0.0"
```

The current build uses:

- Scala `3.8.3`
- Scala.js DOM `2.8.1`
- ES module output targeting `ES2021`

## Minimal Example

```scala
div(
  text("Hello World")
)
```

## Quick Start

The example below creates a small page, mounts it into an existing DOM node, and uses a scoped service.

```scala
import jfx.action.Button.*
import jfx.core.component.ElementComponent.*
import jfx.core.state.Property
import jfx.dsl.*
import jfx.dsl.Scope.{inject, scope, singleton}
import jfx.layout.Div.div
import jfx.layout.VBox.vbox
import org.scalajs.dom.document

object Main {

  final class CounterService {
    val count: Property[Int] = Property(0)

    def increment(): Unit =
      count.set(count.get + 1)
  }

  def main(args: Array[String]): Unit =
    scope {
      singleton[CounterService] {
        new CounterService
      }

      val root = vbox {
        classes = Seq("page", "stack")

        div {
          val counter = inject[CounterService]
          text = s"Count: ${counter.count.get}"

          counter.count.observe { value =>
            text = s"Count: $value"
          }
        }

        button("Increment") {
          buttonType = "button"

          onClick { _ =>
            inject[CounterService].increment()
          }
        }
      }

      document.getElementById("root").appendChild(root.element)
      root.onMount()
    }
}
```

## Building Blocks

### Layout Components

The library ships with layout-oriented components such as `Div`, `HBox`, `VBox`, `Drawer`, `Viewport`, and `Window`.

```scala
import jfx.core.component.ElementComponent.*
import jfx.dsl.*
import jfx.layout.HBox.hbox
import jfx.layout.VBox.vbox

val content = vbox {
  classes = "page"

  hbox {
    classes = "toolbar"

    style {
      justifyContent = "space-between"
      alignItems = "center"
      gap = "12px"
    }
  }
}
```

### State

`Property[T]` is the basic reactive primitive. Observers run immediately with the current value and again on every update.

```scala
import jfx.core.state.Property

val name = Property("Alice")

val subscription = name.observe { current =>
  println(s"Current value: $current")
}

name.set("Bob")
subscription.dispose()
```

Bidirectional synchronization is built in:

```scala
import jfx.core.state.Property

val a = Property("left")
val b = Property("right")

val link = Property.subscribeBidirectional(a, b)

a.set("synced")
println(b.get) // synced

link.dispose()
```

### Styling

Any `ElementComponent` can be styled through the DSL. Styles can be assigned directly or bound from a reactive property.

```scala
import jfx.core.component.ElementComponent.*
import jfx.core.state.Property
import jfx.dsl.*
import jfx.layout.Div.div

val accent = Property("#0f766e")

val badge = div {
  text = "Active"

  style {
    backgroundColor <-- accent
    color = "white"
    padding = "6px 10px"
    borderRadius = "999px"
  }
}
```

## Forms

`Form` and related controls bind themselves to model properties by name. In practice this means your model can expose `Property` fields and the form will connect matching controls automatically.

```scala
import jfx.action.Button.*
import jfx.core.component.ElementComponent.*
import jfx.core.state.Property
import jfx.form.Form.form
import jfx.form.Input.*
import jfx.layout.VBox.vbox

final class PersonFormModel {
  val firstName: Property[String] = Property("")
  val email: Property[String] = Property("")
}

val person = new PersonFormModel

val editor = form(person) {
  vbox {
    input("firstName") {
      placeholder = "First name"
    }

    input("email") {
      placeholder = "Email address"
    }

    button("Save")
  }

  onSubmit = { currentForm =>
    val model = currentForm.valueProperty.get
    println(model.firstName.get)
    println(model.email.get)
  }
}
```

### Validation Annotations

The form layer can derive validators from annotations on model properties.

```scala
import jfx.core.state.Property
import jfx.form.validators.{EmailConstraint, NotBlank, Size}

final class AccountModel {
  @NotBlank("First name is required")
  @Size(min = 2, max = 50, message = "First name must be between 2 and 50 characters")
  val firstName: Property[String] = Property("")

  @EmailConstraint("Enter a valid email address")
  val email: Property[String] = Property("")
}
```

Available built-in annotations include:

- `NotNull`
- `NotEmpty`
- `NotBlank`
- `Size`
- `Min`
- `Max`
- `DecimalMin`
- `DecimalMax`
- `Digits`
- `Pattern`
- `EmailConstraint`

## Routing

`Router` resolves browser URLs against a route tree and renders the matching component. Route factories may be synchronous or asynchronous.

```scala
import jfx.core.component.ElementComponent.*
import jfx.layout.Div.div
import jfx.router.Route
import jfx.router.RouteContext.routeContext
import scala.scalajs.js

val routes = js.Array(
  Route.scoped(
    path = "/",
    factory = {
      div {
        text = "Home"
      }
    },
    children = js.Array(
      Route.scoped(
        path = "/users/:id",
        factory = {
          val ctx = routeContext
          div {
            text = s"User ${ctx.pathParams.get("id").getOrElse("")}"
          }
        }
      ),
      Route.scoped(
        path = "/search",
        factory = {
          val ctx = routeContext
          div {
            text = s"Query: ${ctx.queryParams.get("q").getOrElse("")}"
          }
        }
      )
    )
  )
)
```

Using the router inside a scope:

```scala
import jfx.core.component.NodeComponent.mount
import jfx.dsl.Scope.{inject, scope, singleton}
import jfx.layout.Viewport.viewport
import jfx.router.Router

scope {
  singleton[Router] {
    Router(routes)
  }

  val root = viewport {
    mount(inject[Router])
  }
}
```

Supported route matching features:

- nested route trees
- named path parameters such as `:id`
- wildcard segments with `*`
- query-string parsing
- async route factories via `js.Promise`

## Window-Based Navigation

For desktop-like UI flows in the browser, the library also includes `Window`, `Viewport`, and `WindowRouter`.

```scala
import jfx.router.WindowRouter.windowRouter

val windowedRoot = viewport {
  windowRouter(routes)
}
```

`WindowRouter` opens matched routes inside managed windows and keeps browser navigation in sync with active windows.

## When Should You Use It?

Use `scala-js-jfx` if:

- you build complex UIs such as tables, editors, or dashboards
- you need precise lifecycle control
- you want a structured DSL instead of ad-hoc DOM code

Do not use it if:

- you want minimal boilerplate for very small apps
- you prefer purely functional reactive programming models

## JSON Mapping

`JsonMapper` serializes and deserializes Scala models using reflection metadata. It supports primitives, options, collections, maps, `Property`, `ListProperty`, and polymorphic types.

```scala
import jfx.core.state.Property
import jfx.json.JsonMapper
import scala.scalajs.js

final class Person {
  val firstName: Property[String] = Property("Ada")
  val age: Property[Int] = Property(36)
}

val mapper = new JsonMapper
val person = new Person

val json = mapper.serialize(person)
val copy = mapper.deserialize[Person](json)

println(json.firstName.asInstanceOf[String])
println(copy.age.get)
```

## Positioning

Compared to other approaches:

- vs Laminar: more structured lifecycle control, less implicit magic
- vs React-style frameworks: stronger type safety and no virtual DOM overhead
- vs raw Scala.js DOM: much better composability and maintainability

## Development

Typical local tasks:

```bash
sbt jfx/compile
sbt jfx/test
```

If you want to work on the library inside this repository, focus on the `jfx` module. The `app` module is only a consumer/demo and is not required to use the library itself.

## Publishing To Maven Central

The `jfx` module is configured for publishing via the Sonatype Central Portal with native `sbt` Central support.

Required local setup:

- create `~/.sbt/1.0/credentials.sbt`
- create `~/.sbt/sonatype_central_credentials`
- configure a working GPG installation so `sbt-pgp` can sign artifacts

Example `~/.sbt/1.0/credentials.sbt`:

```scala
credentials += Credentials(Path.userHome / ".sbt" / "sonatype_central_credentials")
```

Example `~/.sbt/sonatype_central_credentials`:

```properties
host=central.sonatype.com
user=<sonatype-user>
password=<sonatype-token>
```

Release commands:

```bash
sbt "; project jfx" "; publishSigned" "; sonaUpload"
sbt "; project jfx" "; publishSigned" "; sonaRelease"
```

Notes:

- snapshots are published to `https://central.sonatype.com/repository/maven-snapshots/`
- releases use `localStaging` and are then uploaded via `sonaUpload`
- only the `jfx` module is intended for Maven Central publication; `app` and `root` are skipped
- run Central commands from the `jfx` project context so the deployment is not started from `root`
