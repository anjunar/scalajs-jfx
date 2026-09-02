package app.pages

import app.components.Showcase.*
import jfx.control.carousel.Carousel
import jfx.control.carousel.Carousel.*
import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.classes
import jfx.core.dsl.EventDsl.onClick
import jfx.core.dsl.StyleDsl.*
import jfx.core.layout.Button.button
import jfx.core.layout.Div.div
import jfx.core.layout.HBox.hbox
import jfx.core.layout.TextComponent.text
import jfx.core.layout.VBox.vbox
import jfx.core.render.Cursor
import jfx.core.state.ListProperty
import jfx.core.i18n.i18n

object CarouselPage {

  final case class SlideCard(kicker: String, title: String, copy: String, accent: String)

  private val showcaseSlides = Seq(
    SlideCard(
      "Atlas",
      "Architecture that keeps moving",
      "The carousel owns the active state while the slide renderer stays declarative.",
      "#2563eb"
    ),
    SlideCard(
      "Signal",
      "Auto-advance without hidden magic",
      "A lifecycle-bound timer rotates the same explicit active-index property.",
      "#0f766e"
    ),
    SlideCard(
      "Northwind",
      "SSR can surface every state",
      "Stable dynamic ranges keep the server and hydration structure aligned.",
      "#ea580c"
    ),
    SlideCard(
      "Harbor",
      "Wrap-around is part of the contract",
      "The next step after the right edge returns to the beginning.",
      "#7c3aed"
    )
  )

  def render()(using AbstractComponent, Cursor): Unit = {
    val slides = ListProperty[SlideCard]()
    slides.setAll(showcaseSlides)

    showcasePage(i18n"Carousel", i18n"Looping slides with explicit state and stable SSR.") {
      vbox {
        style { gap = "34px" }

        sectionIntro(
          i18n"Sequenced content",
          i18n"One active slide, one lifecycle",
          i18n"Navigation, indicators, keyboard input and autoplay all update the same reactive selection."
        )

        metricStrip(
          i18n"Looping"    -> i18n"Next after the last slide starts at the beginning.",
          i18n"Autoplay"   -> i18n"A positive interval advances only while the control is mounted.",
          i18n"SSR states" -> i18n"The server can expose every slide or only the active one."
        )

        componentShowcase(
          i18n"Autoplay carousel",
          i18n"Previous, Next and every indicator remain explicit actions while the timer is active."
        ) {
          vbox {
            style { gap = "16px" }

            val carouselControl = carousel[SlideCard] {
              Carousel.items = slides
              Carousel.autoAdvanceMs = 2600
              Carousel.ssrShowAllStates = true
              Carousel.slideRenderer = (slide: SlideCard, index: Int) => renderSlide(slide, index)
            }

            hbox {
              classes = Seq("showcase-action-row")

              button(i18n"Previous") {
                onClick(_ => carouselControl.previous())
              }

              button(i18n"Next") {
                onClick(_ => carouselControl.next())
              }

              button(i18n"Fast autoplay") {
                onClick(_ => carouselControl.autoAdvanceMsProperty.set(1400))
              }

              button(i18n"Slow autoplay") {
                onClick(_ => carouselControl.autoAdvanceMsProperty.set(3400))
              }

              button(i18n"Stop timer") {
                onClick(_ => carouselControl.autoAdvanceMsProperty.set(0))
              }
            }

            div {
              classes = Seq("showcase-result")
              text(
                carouselControl.activeIndexProperty.flatMap { index =>
                  carouselControl.autoAdvanceMsProperty.map { milliseconds =>
                    s"Active slide: ${index + 1} / ${slides.length} | autoAdvanceMs = $milliseconds"
                  }
                }
              ) {}
            }
          }
        }

        apiSection(
          i18n"Carousel DSL",
          i18n"The contextual renderer owns only the content of one slide."
        ) {
          codeBlock(
            "scala",
            """|carousel[SlideCard] {
               |  items = slides
               |  autoAdvanceMs = 2600
               |  ssrShowAllStates = true
               |  slideRenderer = (slide: SlideCard, index: Int) =>
               |    renderSlide(slide, index)
               |}""".stripMargin
          )
        }

        insightGrid(
          (
            i18n"Loop",
            i18n"No dead right edge",
            i18n"next(), previous() and indicators share the same normalized active index."
          ),
          (
            i18n"Timer",
            i18n"Autoplay remains disposable",
            i18n"Changing the interval replaces the timer; unmounting always clears it."
          ),
          (
            i18n"Hydration",
            i18n"Both modes keep one tree shape",
            i18n"Active-only mode uses a dynamic mount point on the server and in the browser."
          )
        )
      }
    }
  }

  private def renderSlide(slide: SlideCard, index: Int)(using
      AbstractComponent,
      Cursor
  ): Unit =
    vbox {
      classes = Seq("carousel-demo-slide")
      style {
        minHeight = "320px"
        padding = "28px"
        boxSizing = "border-box"
      }

      div {
        classes = Seq("carousel-demo-slide__kicker")
        text(slide.kicker) {}
      }

      div {
        classes = Seq("carousel-demo-slide__title")
        text(s"${index + 1}. ${slide.title}") {}
      }

      div {
        classes = Seq("carousel-demo-slide__copy")
        text(slide.copy) {}
      }

      div {
        classes = Seq("carousel-demo-slide__footer")

        div {
          classes = Seq("carousel-demo-slide__pill")
          style { background = slide.accent }
          text("State") {}
        }

        div {
          classes = Seq("carousel-demo-slide__accent")
          style { color = slide.accent }
          text("Looping sequence") {}
        }
      }
    }
}
