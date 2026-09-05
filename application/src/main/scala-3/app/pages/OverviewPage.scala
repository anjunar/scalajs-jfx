package app.pages

import app.components.Showcase.*
import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.classes
import jfx.core.dsl.EventDsl.onClick
import jfx.core.dsl.StyleDsl.*
import jfx.core.layout.Button.button
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.layout.VBox.vbox
import jfx.core.render.Cursor
import jfx.core.i18n.{I18nRuntime, RuntimeMessage, i18n}

object OverviewPage {
  def render()(using AbstractComponent, Cursor): Unit = {
    val runtime = I18nRuntime.require
    showcasePage(i18n"Welcome to JFX 3", i18n"Reactive Scala.js interfaces with SSR and hydration built into the same component model.") {
      vbox {
        style { gap = "34px" }
        componentShowcase(
          i18n"Start with working code",
          i18n"A Property drives the text, and the event updates that same state after hydration."
        ) {
          codeBlock(
            "scala",
            """import jfx.core.dsl.EventDsl.onClick
import jfx.core.layout.Button.button
import jfx.core.layout.TextComponent.text
import jfx.core.layout.VBox.vbox
import jfx.core.state.Property

val count = Property(0)

vbox {
  text(count.map(n => s"Count: $n")) {}
  button("Increment") {
    onClick(_ => count.set(count.get + 1))
  }
}"""
          )
        }
        metricStrip(
          i18n"SSR"  -> i18n"Server HTML and client hydration share the same structure.",
          i18n"DSL"  -> i18n"Templates stay declarative and free of DOM handwork.",
          i18n"Live" -> i18n"Every page shows a usable example instead of a dry API list."
        )
        sectionIntro(
          i18n"One component model",
          i18n"Render complete HTML on the server, then hydrate the same tree in the browser.",
          i18n"The declarative Scala DSL, reactive properties, router, forms, and lifecycle-aware components share one runtime. The TypeScript packages expose that runtime through a typed facade."
        )
        componentShowcase(
          i18n"Message-centered I18n",
          i18n"The English source lives in Scala code. The catalog attaches multiple languages to exactly that one message."
        ) {
          vbox {
            classes = Seq("i18n-demo")
            div {
              classes = Seq("i18n-demo__toolbar")
              div {
                classes = Seq("i18n-demo__locale");
                text(runtime.locale.map(locale => s"Locale: ${locale.code}")) {}
              }
              button(i18n"Switch locale") {
                classes = Seq("calm-action", "calm-action--secondary");
                onClick { _ =>
                  runtime.setLocale(
                    if (runtime.locale.get.code == "de") jfx.core.i18n.I18nLocale.En
                    else jfx.core.i18n.I18nLocale("de")
                  )
                }
              }
            }
            div {
              classes = Seq("i18n-demo__grid")
              i18nSample("""i18n"Delete document"""", i18n"Delete document")
              i18nSample(
                """i18n"User $user invited you to $group"""",
                i18n"User ${jfx.core.i18n.I18n.named("user", "Mira")} invited you to ${jfx.core.i18n.I18n.named("group", "Core Team")}"
              )
              i18nSample(
                """i18n"Missing translations fall back to English"""",
                i18n"Missing translations fall back to English"
              )
            }
          }
        }
        insightGrid(
          (
            i18n"01",
            i18n"Readability first",
            i18n"Components are shown so their purpose, state, and placement are immediately clear."
          ),
          (
            i18n"02",
            i18n"Hydration in view",
            i18n"Examples avoid hidden DOM drift and keep virtual containers understandable."
          ),
          (
            i18n"03",
            i18n"A growing system",
            i18n"New components get room for context, variants, API, and architectural hints."
          )
        )
        patternList(
          i18n"What you find on the component pages",
          i18n"A short explanation of when the component makes sense.",
          i18n"At least one real live state with data or interaction.",
          i18n"Concrete DSL examples that stay close to production code.",
          i18n"Notes about stability, cursor behavior, SSR, or reactive properties."
        )
        noteBlock(
          i18n"Next step",
          i18n"Pick a component on the left. Each page is now denser and still leaves room for more building blocks without losing the thread."
        )
      }
    }
  }

  private def i18nSample(source: String, resolved: RuntimeMessage)(using
      AbstractComponent,
      Cursor
  ): Unit = {
    vbox {
      classes = Seq("i18n-demo__sample")
      div { classes = Seq("i18n-demo__label"); text(i18n"Source") {} }
      div { classes = Seq("i18n-demo__source"); text(source) {} }
      div { classes = Seq("i18n-demo__label"); text(i18n"Resolved") {} }
      div { classes = Seq("i18n-demo__resolved"); text(resolved) {} }
    }
  }
}
