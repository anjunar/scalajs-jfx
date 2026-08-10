package app.pages

import app.AppI18n
import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.classes
import jfx.core.dsl.EventDsl.onClick
import jfx.core.layout.Button.button
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.render.Cursor
import jfx.i18n.{I18nRuntime, RuntimeMessage, i18n}
import jfx.router.Router

object OverviewPage {

  def render()(using AbstractComponent, Cursor): Unit = {
    val locale =
      I18nRuntime.require.locale

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
      locale: jfx.core.state.ReadOnlyProperty[jfx.i18n.I18nLocale],
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
      locale: jfx.core.state.ReadOnlyProperty[jfx.i18n.I18nLocale],
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
            s"${AppI18n.resolve(subtitle, current)} ${AppI18n.resolve(body, current)}"
          }
        ) {}
      }
    }

  private def demoCard(
      locale: jfx.core.state.ReadOnlyProperty[jfx.i18n.I18nLocale],
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
      locale: jfx.core.state.ReadOnlyProperty[jfx.i18n.I18nLocale],
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
