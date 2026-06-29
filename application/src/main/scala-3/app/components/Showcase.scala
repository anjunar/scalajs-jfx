package app.components

import app.components.Dsl.classes
import app.components.Layouts.vbox
import jfx.core.component.AbstractComponent
import jfx.core.render.Cursor
import jfx.core.state.{Property, ReadOnlyProperty}
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.i18n.RuntimeMessage

object Showcase {

  def showcasePage(
      title: String,
      subtitle: String
  )(content: => Unit)(using AbstractComponent, Cursor): Unit =
    showcasePage(Property(title), Property(subtitle))(content)

  def showcasePage(
      title: RuntimeMessage,
      subtitle: RuntimeMessage
  )(content: => Unit)(using AbstractComponent, Cursor): Unit = {
    vbox {
      classes = Seq("showcase-page")

      vbox {
        classes = Seq("showcase-page__header")
        div { classes = Seq("showcase-page__eyebrow"); text("scalajs-jfx") {} }
        div { classes = Seq("showcase-page__title"); text(title) {} }
        div { classes = Seq("showcase-page__subtitle"); text(subtitle) {} }
      }

      div {
        classes = Seq("showcase-page__content")
        content
      }
    }
  }

  def showcasePage(
      title: ReadOnlyProperty[String],
      subtitle: ReadOnlyProperty[String]
  )(content: => Unit)(using AbstractComponent, Cursor): Unit = {
    vbox {
      classes = Seq("showcase-page")

      vbox {
        classes = Seq("showcase-page__header")
        div { classes = Seq("showcase-page__eyebrow"); text("scalajs-jfx") {} }
        div { classes = Seq("showcase-page__title"); text(title) {} }
        div { classes = Seq("showcase-page__subtitle"); text(subtitle) {} }
      }

      div {
        classes = Seq("showcase-page__content")
        content
      }
    }
  }

  def sectionIntro(
      kicker: String,
      title: String,
      body: String
  )(using AbstractComponent, Cursor): Unit =
    sectionIntro(Property(kicker), Property(title), Property(body))

  def sectionIntro(
      kicker: RuntimeMessage,
      title: RuntimeMessage,
      body: RuntimeMessage
  )(using AbstractComponent, Cursor): Unit = {
    vbox {
      classes = Seq("showcase-section-intro")
      div { classes = Seq("showcase-section-intro__kicker"); text(kicker) {} }
      div { classes = Seq("showcase-section-intro__title"); text(title) {} }
      div { classes = Seq("showcase-section-intro__body"); text(body) {} }
    }
  }

  def sectionIntro(
      kicker: ReadOnlyProperty[String],
      title: ReadOnlyProperty[String],
      body: ReadOnlyProperty[String]
  )(using AbstractComponent, Cursor): Unit = {
    vbox {
      classes = Seq("showcase-section-intro")
      div { classes = Seq("showcase-section-intro__kicker"); text(kicker) {} }
      div { classes = Seq("showcase-section-intro__title"); text(title) {} }
      div { classes = Seq("showcase-section-intro__body"); text(body) {} }
    }
  }

  def metricStrip(items: (String, String)*)(using AbstractComponent, Cursor): Unit = {
    div {
      classes = Seq("showcase-metric-strip")

      items.foreach { case (value, label) =>
        vbox {
          classes = Seq("showcase-metric")
          div { classes = Seq("showcase-metric__value"); text(value) {} }
          div { classes = Seq("showcase-metric__label"); text(label) {} }
        }
      }
    }
  }

  def insightGrid(items: (String, String, String)*)(using AbstractComponent, Cursor): Unit = {
    div {
      classes = Seq("showcase-insight-grid")

      items.zipWithIndex.foreach { case ((label, title, body), index) =>
        vbox {
          classes = Seq("showcase-insight", s"showcase-insight--${index % 3}")
          div { classes = Seq("showcase-insight__label"); text(label) {} }
          div { classes = Seq("showcase-insight__title"); text(title) {} }
          div { classes = Seq("showcase-insight__body"); text(body) {} }
        }
      }
    }
  }

  def componentShowcase(
      title: String,
      summary: String = ""
  )(content: => Unit)(using AbstractComponent, Cursor): Unit = {
    vbox {
      classes = Seq("component-showcase")

      vbox {
        classes = Seq("component-showcase__header")
        div { classes = Seq("component-showcase__title"); text(title) {} }

        if (summary.nonEmpty) {
          div { classes = Seq("component-showcase__summary"); text(summary) {} }
        }
      }

      div {
        classes = Seq("component-showcase__render")
        content
      }
    }
  }

  def componentShowcase(
      title: RuntimeMessage,
      summary: RuntimeMessage
  )(content: => Unit)(using AbstractComponent, Cursor): Unit = {
    vbox {
      classes = Seq("component-showcase")

      vbox {
        classes = Seq("component-showcase__header")
        div { classes = Seq("component-showcase__title"); text(title) {} }
        div { classes = Seq("component-showcase__summary"); text(summary) {} }
      }

      div {
        classes = Seq("component-showcase__render")
        content
      }
    }
  }

  def apiSection(
      title: String,
      summary: String = ""
  )(content: => Unit)(using AbstractComponent, Cursor): Unit = {
    vbox {
      classes = Seq("api-section")

      vbox {
        classes = Seq("api-section__header")
        div { classes = Seq("api-section__title"); text(title) {} }

        if (summary.nonEmpty) {
          div { classes = Seq("api-section__summary"); text(summary) {} }
        }
      }

      div {
        classes = Seq("api-section__content")
        content
      }
    }
  }

  def apiSection(
      title: RuntimeMessage,
      summary: RuntimeMessage
  )(content: => Unit)(using AbstractComponent, Cursor): Unit = {
    vbox {
      classes = Seq("api-section")

      vbox {
        classes = Seq("api-section__header")
        div { classes = Seq("api-section__title"); text(title) {} }
        div { classes = Seq("api-section__summary"); text(summary) {} }
      }

      div {
        classes = Seq("api-section__content")
        content
      }
    }
  }

  def codeBlock(language: String, code: String)(using AbstractComponent, Cursor): Unit = {
    vbox {
      classes = Seq("code-block")
      div { classes = Seq("code-block__lang"); text(language) {} }
      div { classes = Seq("code-block__content"); text(code) {} }
    }
  }

  def stateChip(label: String, modifier: String)(using AbstractComponent, Cursor): Unit = {
    div {
      classes = Seq("app-state-chip", s"is-$modifier")
      text(label) {}
    }
  }
}
