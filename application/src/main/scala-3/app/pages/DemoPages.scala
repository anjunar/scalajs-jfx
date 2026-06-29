package app.pages

import app.DemoI18n
import app.components.Dsl.{classIf, classes, onClick}
import app.components.Layouts.vbox
import app.components.Showcase
import jfx.core.component.AbstractComponent
import jfx.core.state.{Property, ReadOnlyProperty}
import jfx.core.layout.Button.button
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.render.Cursor
import jfx.i18n.{I18n, I18nLocale, RuntimeMessage, i18n}
import jfx.layout.Viewport
import jfx.layout.Viewport.NotificationKind
import jfx.router.RouteContext
import jfx.router.Router

object OverviewPage {

  def render(locale: ReadOnlyProperty[I18nLocale])(using AbstractComponent, Cursor): Unit = {
    div {
      classes = Seq("clarity-page", "clarity-page--home")

      div {
        classes = Seq("home-hero")

        div {
          classes = Seq("home-hero__content")

          div {
            classes = Seq("home-eyebrow")
            text(i18n"Scala.js UI architecture") {}
          }

          div {
            classes = Seq("home-hero__title")
            text(i18n"A fresh demo, rebuilt around the actual scalajs-jfx modules.") {}
          }

          div {
            classes = Seq("home-hero__copy")
            text(i18n"The visual language mirrors the JFX2 showcase, but the pages here are written specifically for this repository: router, i18n, viewport, forms and rendering infrastructure.") {}
          }

          div {
            classes = Seq("home-hero__actions", "clarity-action-row")

            button(i18n"Router") {
              classes = Seq("calm-action", "calm-action--primary")
              onClick { _ => Router.navigate("/router") }
            }

            button(i18n"Viewport") {
              classes = Seq("calm-action", "calm-action--secondary")
              onClick { _ => Router.navigate("/viewport") }
            }
          }
        }

        div {
          classes = Seq("home-hero__metrics")

          metricCard(
            locale,
            "01",
            "core",
            i18n"The rendering DSL, properties and lifecycle foundations."
          )
          metricCard(
            locale,
            "02",
            "router",
            i18n"Base-path aware navigation with locale prefixes and async route loading."
          )
          metricCard(
            locale,
            "03",
            "viewport",
            i18n"Windows and notifications as global UI surfaces."
          )
        }
      }

      div {
        classes = Seq("home-section", "home-section--intro")
        sectionHeading(
          locale,
          i18n"Modules",
          i18n"What this app chooses to make visible.",
          i18n"Each page isolates one subsystem and explains the tradeoffs in its own voice instead of imitating a generated docs tree."
        )

        div {
          classes = Seq("home-benefit-grid")
          benefitCard(locale, i18n"Router", i18n"Locale-aware paths", i18n"Routes stay matchable while the browser URL keeps `/scalajs-jfx/de/...` visible.")
          benefitCard(locale, i18n"i18n", i18n"Message model", i18n"The repository already contains a source-first i18n model, so the demo shows where URL locale and runtime locale meet.")
          benefitCard(locale, i18n"Forms", i18n"Field architecture", i18n"Forms are documented as composable controls with explicit registration and validation structure.")
          benefitCard(locale, i18n"Viewport", i18n"Global stage", i18n"Notifications and windows are rendered once and reused across routes.")
        }
      }

      div {
        classes = Seq("home-section")
        sectionHeading(
          locale,
          i18n"Explore",
          i18n"Jump directly into the rebuilt pages.",
          i18n"The shell design is inherited from JFX2, but every content block below is newly written for this codebase."
        )

        div {
          classes = Seq("home-demo-grid")
          demoCard(locale, "01", i18n"Router", i18n"Path resolution, route context and locale prefixes.", "/router")
          demoCard(locale, "02", i18n"i18n", i18n"Toolbar locale switch, route prefixes and catalog direction.", "/i18n")
          demoCard(locale, "03", i18n"Rendering", i18n"SSR, hydration and route loading constraints.", "/rendering")
          demoCard(locale, "04", i18n"State", i18n"Reactive properties as the smallest moving part.", "/state")
        }
      }

      div {
        classes = Seq("home-section--closing")

        div {
          classes = Seq("home-closing__copy")
          div {
            classes = Seq("home-closing__title")
            text(i18n"The shell is familiar. The story is new.") {}
          }
          div {
            classes = Seq("home-closing__body")
            text(i18n"This demo is intentionally narrower than JFX2: it shows the real building blocks that exist in this repository and avoids pretending that missing modules are already here.") {}
          }
        }

        button(i18n"Open router docs") {
          classes = Seq("calm-action", "calm-action--primary")
          onClick { _ => Router.navigate("/router") }
        }
      }
    }
  }

  private def metricCard(
      locale: ReadOnlyProperty[I18nLocale],
      index: String,
      title: String,
      body: RuntimeMessage
  )(using AbstractComponent, Cursor): Unit =
    div {
      classes = Seq("home-metric")
      div { classes = Seq("home-metric__index"); text(index) {} }
      div { classes = Seq("home-metric__title"); text(title) {} }
      div { classes = Seq("home-metric__body"); text(body) {} }
    }

  private def benefitCard(
      locale: ReadOnlyProperty[I18nLocale],
      title: RuntimeMessage,
      subtitle: RuntimeMessage,
      body: RuntimeMessage
  )(using AbstractComponent, Cursor): Unit =
    div {
      classes = Seq("home-benefit-card")
      div { classes = Seq("home-benefit-card__title"); text(title) {} }
      div {
        classes = Seq("home-benefit-card__body")
        text(
          locale.map { current =>
            s"${DemoI18n.resolve(subtitle, current)} ${DemoI18n.resolve(body, current)}"
          }
        ) {}
      }
    }

  private def demoCard(
      locale: ReadOnlyProperty[I18nLocale],
      meta: String,
      title: RuntimeMessage,
      body: RuntimeMessage,
      path: String
  )(using AbstractComponent, Cursor): Unit =
    div {
      classes = Seq("home-demo-card")
      div {
        classes = Seq("home-demo-card__meta")
        text(meta) {}
      }
      div {
        classes = Seq("home-demo-card__title")
        text(title) {}
      }
      div {
        classes = Seq("home-demo-card__body")
        text(body) {}
      }
      button(i18n"Open") {
        classes = Seq("calm-action", "calm-action--secondary")
        onClick { _ => Router.navigate(path) }
      }
    }

  private def sectionHeading(
      locale: ReadOnlyProperty[I18nLocale],
      label: RuntimeMessage,
      title: RuntimeMessage,
      copy: RuntimeMessage
  )(using AbstractComponent, Cursor): Unit =
    div {
      classes = Seq("home-section-heading")
      div { classes = Seq("home-eyebrow"); text(label) {} }
      div { classes = Seq("home-section-heading__title"); text(title) {} }
      div { classes = Seq("home-section-heading__copy"); text(copy) {} }
    }
}

object RouterPage {
  def render(locale: ReadOnlyProperty[I18nLocale])(using AbstractComponent, Cursor): Unit = {
    Showcase.showcasePage(
      i18n"Router & route model",
      i18n"Base path, locale prefix and explicit route context now live in one coherent flow."
    ) {
      Showcase.sectionIntro(
        i18n"Contract",
        i18n"Only async routes remain",
        i18n"The route loader always receives a RouteContext and always returns a Future[AbstractComponent]. There is no second synchronous API surface to drift away anymore."
      )

      Showcase.metricStrip(
        "basePath" -> "/scalajs-jfx",
        "locale" -> "/de or /en",
        "load" -> "Future[AbstractComponent]"
      )

      Showcase.insightGrid(
        ("URL", "Browser path stays human", "The router strips base path and locale for matching, then restores both for history updates."),
        ("Context", "Route data is explicit", "Loaders get path params, locale, browserPath and query params as a plain value."),
        ("Hydration", "Initial route must stay immediate", "Hydration still requires the first route to resolve synchronously from the Future state.")
      )

      Showcase.componentShowcase(
        i18n"Route context demo",
        i18n"This button leads to a route with an explicit path parameter."
      ) {
        button(i18n"Open /router/user/42") {
          classes = Seq("calm-action", "calm-action--primary")
          onClick { _ => Router.navigate("/router/user/42") }
        }
      }

      Showcase.apiSection(
        i18n"Current route shape",
        i18n"The demo uses the same API as downstream applications would."
      ) {
        Showcase.codeBlock(
          "scala",
          """Route.view("/router") { context =>
            |  Future.successful {
            |    Route.component {
            |      // render page with explicit RouteContext
            |    }
            |  }
            |}""".stripMargin
        )
      }
    }
  }
}

object RouterUserPage {
  def render(
      locale: ReadOnlyProperty[I18nLocale],
      context: RouteContext
  )(using AbstractComponent, Cursor): Unit = {
    Showcase.showcasePage(
      i18n"Explicit route context",
      i18n"This page exists to prove that path params no longer arrive through Route.requireContext."
    ) {
      Showcase.metricStrip(
        "id" -> context.pathParams.getOrElse("id", "?"),
        "path" -> context.path,
        "locale" -> context.locale.map(_.code).getOrElse("none")
      )

      Showcase.apiSection(
        i18n"Loader input",
        i18n"The route parameter is read directly from the loader argument."
      ) {
        Showcase.codeBlock(
          "scala",
          s"""Route.view("/router/user/:id") { context =>
             |  val id = context.pathParams("id")
             |  Future.successful(Route.component { ... })
             |}""".stripMargin
        )
      }
    }
  }
}

object I18nPage {
  def render(locale: ReadOnlyProperty[I18nLocale])(using AbstractComponent, Cursor): Unit = {
    Showcase.showcasePage(
      i18n"i18n & locale routing",
      i18n"The toolbar locale switch now aligns with locale-prefixed routes instead of living beside them."
    ) {
      Showcase.sectionIntro(
        i18n"Direction",
        i18n"URL locale first, message locale second",
        i18n"The route decides the current locale. Text helpers then resolve visible copy from that one property."
      )

      Showcase.metricStrip(
        "current" -> locale.map(_.code).get,
        "prefixes" -> "/de, /en",
        "fallback" -> "en"
      )

      Showcase.insightGrid(
        ("Route", "Locale is part of the path", "Direct URLs, SSR and client navigation now agree on the same prefix semantics."),
        ("Toolbar", "Switch keeps the current page", "Changing locale rewrites the URL but preserves the matched application path."),
        ("Catalog", "Ready for message-based i18n", "The repository already contains a richer i18n model that can replace the lightweight demo copy step by step.")
      )

      Showcase.apiSection(
        i18n"Lightweight demo copy",
        i18n"The visual design is ported first; the full message catalog can grow from here."
      ) {
        Showcase.codeBlock(
          "scala",
          """DemoI18n.text(i18n"Router", localeProperty)""".stripMargin
        )
      }
    }
  }
}

object RenderingPage {
  def render(locale: ReadOnlyProperty[I18nLocale])(using AbstractComponent, Cursor): Unit = {
    Showcase.showcasePage(
      i18n"Rendering, SSR & hydration",
      i18n"The app shell is server-rendered, hydrated on the client and still keeps route loading honest."
    ) {
      Showcase.insightGrid(
        ("SSR", "The initial URL is injected explicitly", "The demo no longer relies on an implicit request header path inside the router call site."),
        ("Hydration", "Initial route must already exist", "The router still guards against asynchronous hydration drift on first load."),
        ("Shell", "Toolbar and navigation stay stable", "The visual frame does not reflow unexpectedly while the routed content swaps.")
      )

      Showcase.apiSection(
        i18n"Boot flow",
        i18n"Client and SSR both hand the initial URL to App explicitly."
      ) {
        Showcase.codeBlock(
          "scala",
          """Runtime.renderToStringAsync { cursor =>
            |  render(cursor, request, path)
            |}
            |
            |render(hydratingCursor, request, url)""".stripMargin
        )
      }
    }
  }
}

object StatePage {
  def render(locale: ReadOnlyProperty[I18nLocale])(using AbstractComponent, Cursor): Unit = {
    val counter =
      Property(0)

    val status =
      counter.map { value =>
        DemoI18n.resolve(i18n"Current value: ${I18n.named("value", value)}", locale.get)
      }

    Showcase.showcasePage(
      i18n"Reactive state",
      i18n"Properties are still the smallest honest abstraction in the system."
    ) {
      Showcase.componentShowcase(
        i18n"Counter",
        i18n"A tiny interaction is enough to make the data flow visible."
      ) {
        vbox {
          classes = Seq("clarity-grid")

          div {
            classes = Seq("docs-card")
            div { classes = Seq("docs-card__title"); text(status) {} }
            div { classes = Seq("docs-card__summary"); text(i18n"The visible text is derived directly from a Property[Int].") {} }
          }

          div {
            classes = Seq("clarity-action-row")

            button(i18n"Increment") {
              classes = Seq("calm-action", "calm-action--primary")
              onClick { _ => counter.set(counter.get + 1) }
            }

            button(i18n"Reset") {
              classes = Seq("calm-action", "calm-action--secondary")
              onClick { _ => counter.set(0) }
            }
          }
        }
      }
    }
  }
}

object FormsPage {
  def render(locale: ReadOnlyProperty[I18nLocale])(using AbstractComponent, Cursor): Unit = {
    val activeStage =
      Property("register")

    def stageButton(id: String, label: RuntimeMessage): Unit =
      button(label) {
        classes = Seq("form-page__transition-button")
        classIf("is-active", activeStage.map(_ == id))
        onClick { _ => activeStage.set(id) }
      }

    Showcase.showcasePage(
      i18n"Forms architecture",
      i18n"The demo documents the form model without pretending that JFX2 controls already exist here."
    ) {
      Showcase.sectionIntro(
        i18n"Focus",
        i18n"Registration, control contract and shared context",
        i18n"jfx-forms is present in this repository, but the visual showcase is rewritten around the architecture instead of copying a feature matrix from another project."
      )

      Showcase.componentShowcase(
        i18n"Lifecycle states",
        i18n"These buttons describe how the form stack is wired."
      ) {
        vbox {
          classes = Seq("form-page__state-strip")

          div {
            classes = Seq("form-page__transition-row")
            stageButton("register", i18n"Register controls")
            stageButton("bind", i18n"Bind values")
            stageButton("validate", i18n"Validate")
          }

          div {
            classes = Seq("form-page__prompt")
            div { classes = Seq("form-page__prompt-title"); text(activeStage.map {
              case "register" => DemoI18n.resolve(i18n"Registration", locale.get)
              case "bind"     => DemoI18n.resolve(i18n"Binding", locale.get)
              case _          => DemoI18n.resolve(i18n"Validation", locale.get)
            }) {} }
            div { classes = Seq("form-page__prompt-copy"); text(activeStage.map {
              case "register" => DemoI18n.resolve(i18n"Inputs register themselves through FormContext so the form owns a concrete field map.", locale.get)
              case "bind"     => DemoI18n.resolve(i18n"Controls then expose their own contract for reading and writing values.", locale.get)
              case _          => DemoI18n.resolve(i18n"Validation stays near the control layer instead of hiding in a remote action handler.", locale.get)
            }) {} }
          }
        }
      }

      Showcase.apiSection(
        i18n"Current primitives",
        i18n"What exists right now in this repository."
      ) {
        Showcase.codeBlock(
          "scala",
          """Form.form {
            |  Input.input("name") {
            |    // registers itself through FormContext
            |  }
            |}""".stripMargin
        )
      }
    }
  }
}

object ViewportPage {
  def render(locale: ReadOnlyProperty[I18nLocale])(using AbstractComponent, Cursor): Unit = {
    Showcase.showcasePage(
      i18n"Viewport surfaces",
      i18n"Notifications and windows are still one of the strongest interactive stories in this repository."
    ) {
      Showcase.componentShowcase(
        i18n"Interactive stage",
        i18n"Open a notification or a window from the routed page."
      ) {
        div {
          classes = Seq("clarity-action-row")

          button(i18n"Notify") {
            classes = Seq("calm-action", "calm-action--primary")
            onClick { _ =>
              Viewport.notify(
                DemoI18n.resolve(i18n"Viewport notification from the rebuilt demo.", locale.get),
                NotificationKind.Success
              )
            }
          }

          button(i18n"Open window") {
            classes = Seq("calm-action", "calm-action--secondary")
            onClick { _ =>
              Viewport.addWindow(DemoI18n.resolve(i18n"Viewport window", locale.get)) {
                vbox {
                  classes = Seq("window-page__launch-card")
                  div {
                    classes = Seq("window-page__launch-title")
                    text(i18n"Global viewport window") {}
                  }
                  div {
                    classes = Seq("window-page__launch-copy")
                    text(i18n"This content is mounted into the shared viewport layer, not into the route subtree.") {}
                  }
                }
              }
            }
          }
        }
      }

      Showcase.insightGrid(
        ("Global", "Rendered once", "The viewport owns windows and notifications as central lists."),
        ("Layered", "Outside the route subtree", "Routed content triggers overlays without coupling itself to local DOM hacks."),
        ("Composable", "Still ordinary components", "Window bodies are written with the same DSL as the rest of the app.")
      )
    }
  }
}
