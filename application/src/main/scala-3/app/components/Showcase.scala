package app.components

import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.classes
import jfx.core.dsl.DslLayer.{child, render, renderInto}
import jfx.core.dsl.EventDsl.onClick
import jfx.core.layout.Button.button
import jfx.core.layout.Div
import jfx.core.layout.Div.div
import jfx.core.layout.Heading
import jfx.core.layout.TextComponent.text
import jfx.core.layout.VBox.vbox
import jfx.core.render.Cursor
import jfx.core.state.{Property, ReadOnlyProperty}
import jfx.core.i18n.RuntimeMessage
import org.scalajs.dom

import scala.annotation.targetName
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js.JSConverters.*
import scala.util.{Failure, Success}

object Showcase {

  def showcasePage(
      title: String,
      subtitle: String
  )(content: AbstractComponent ?=> Cursor ?=> Unit)(using AbstractComponent, Cursor): Unit =
    showcasePage(Property(title), Property(subtitle))(content)

  def showcasePage(
      title: RuntimeMessage,
      subtitle: RuntimeMessage
  )(
      content: AbstractComponent ?=> Cursor ?=> Unit
  )(using parent: AbstractComponent, cursor: Cursor): Unit = {
    var contentHost: Div = null

    vbox {
      classes = Seq("showcase-page")

      vbox {
        classes = Seq("showcase-page__header")
        div { classes = Seq("showcase-page__eyebrow"); text("scalajs-jfx") {} }
        child(new Heading(1)) { classes = Seq("showcase-page__title"); text(title) {} }
        div { classes = Seq("showcase-page__subtitle"); text(subtitle) {} }
      }

      contentHost = div {
        classes = Seq("showcase-page__content")
      }
    }

    renderInto(contentHost) {
      content
    }
  }

  def showcasePage(
      title: ReadOnlyProperty[String],
      subtitle: ReadOnlyProperty[String]
  )(content: AbstractComponent ?=> Cursor ?=> Unit)(using AbstractComponent, Cursor): Unit =
    renderShowcasePage(title, subtitle)(content)

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

  @targetName("metricStripMessages")
  def metricStrip(
      items: (RuntimeMessage, RuntimeMessage)*
  )(using AbstractComponent, Cursor): Unit = {
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

  @targetName("insightGridMessages")
  def insightGrid(items: (RuntimeMessage, RuntimeMessage, RuntimeMessage)*)(using
      AbstractComponent,
      Cursor
  ): Unit = {
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

  def noteBlock(title: RuntimeMessage, body: RuntimeMessage)(using
      AbstractComponent,
      Cursor
  ): Unit = {
    vbox {
      classes = Seq("showcase-note")
      div { classes = Seq("showcase-note__title"); text(title) {} }
      div { classes = Seq("showcase-note__body"); text(body) {} }
    }
  }

  def patternList(title: RuntimeMessage, items: RuntimeMessage*)(using
      AbstractComponent,
      Cursor
  ): Unit = {
    vbox {
      classes = Seq("showcase-pattern-list")
      div { classes = Seq("showcase-pattern-list__title"); text(title) {} }
      div {
        classes = Seq("showcase-pattern-list__items")
        items.foreach { item =>
          div { classes = Seq("showcase-pattern-list__item"); text(item) {} }
        }
      }
    }
  }

  def componentShowcase(
      title: String,
      summary: String = ""
  )(content: AbstractComponent ?=> Cursor ?=> Unit)(using AbstractComponent, Cursor): Unit =
    renderComponentShowcase(Property(title), Option.when(summary.nonEmpty)(Property(summary)))(
      content
    )

  def componentShowcase(
      title: RuntimeMessage,
      summary: RuntimeMessage
  )(
      content: AbstractComponent ?=> Cursor ?=> Unit
  )(using parent: AbstractComponent, cursor: Cursor): Unit = {
    var contentHost: Div = null

    vbox {
      classes = Seq("component-showcase")

      vbox {
        classes = Seq("component-showcase__header")
        div { classes = Seq("component-showcase__title"); text(title) {} }
        div { classes = Seq("component-showcase__summary"); text(summary) {} }
      }

      contentHost = div {
        classes = Seq("component-showcase__render")
      }
    }

    renderInto(contentHost) {
      content
    }
  }

  def apiSection(
      title: String,
      summary: String = ""
  )(content: AbstractComponent ?=> Cursor ?=> Unit)(using AbstractComponent, Cursor): Unit =
    renderApiSection(Property(title), Option.when(summary.nonEmpty)(Property(summary)))(content)

  def apiSection(
      title: RuntimeMessage,
      summary: RuntimeMessage
  )(
      content: AbstractComponent ?=> Cursor ?=> Unit
  )(using parent: AbstractComponent, cursor: Cursor): Unit = {
    var contentHost: Div = null

    vbox {
      classes = Seq("api-section")

      vbox {
        classes = Seq("api-section__header")
        div { classes = Seq("api-section__title"); text(title) {} }
        div { classes = Seq("api-section__summary"); text(summary) {} }
      }

      contentHost = div {
        classes = Seq("api-section__content")
      }
    }

    renderInto(contentHost) {
      content
    }
  }

  def codeBlock(language: String, code: String)(using AbstractComponent, Cursor): Unit = {
    val copyLabel = Property("Copy")

    vbox {
      classes = Seq("code-block")
      div {
        classes = Seq("code-block__header")
        div { classes = Seq("code-block__lang"); text(language) {} }
        button(copyLabel) {
          classes = Seq("code-block__copy")
          onClick { _ =>
            if (Cursor.isBrowser) {
              try {
                dom.window.navigator.clipboard.writeText(code).toFuture.onComplete {
                  case Success(_) => copyLabel.set("Copied")
                  case Failure(_) => copyLabel.set("Copy failed")
                }
              } catch {
                case _: Throwable => copyLabel.set("Copy failed")
              }
            }
          }
        }
      }
      div { classes = Seq("code-block__content"); text(code) {} }
    }
  }

  def stateChip(label: String, modifier: String)(using AbstractComponent, Cursor): Unit = {
    div {
      classes = Seq("app-state-chip", s"is-$modifier")
      text(label) {}
    }
  }

  private def renderShowcasePage(
      title: ReadOnlyProperty[String],
      subtitle: ReadOnlyProperty[String]
  )(
      content: AbstractComponent ?=> Cursor ?=> Unit
  )(using parent: AbstractComponent, cursor: Cursor): Unit = {
    var contentHost: Div = null

    vbox {
      classes = Seq("showcase-page")

      vbox {
        classes = Seq("showcase-page__header")
        div { classes = Seq("showcase-page__eyebrow"); text("scalajs-jfx") {} }
        child(new Heading(1)) { classes = Seq("showcase-page__title"); text(title) {} }
        div { classes = Seq("showcase-page__subtitle"); text(subtitle) {} }
      }

      contentHost = div {
        classes = Seq("showcase-page__content")
      }
    }

    renderInto(contentHost) {
      content
    }
  }

  private def renderComponentShowcase(
      title: ReadOnlyProperty[String],
      summary: Option[ReadOnlyProperty[String]]
  )(
      content: AbstractComponent ?=> Cursor ?=> Unit
  )(using parent: AbstractComponent, cursor: Cursor): Unit = {
    var contentHost: Div = null

    vbox {
      classes = Seq("component-showcase")

      vbox {
        classes = Seq("component-showcase__header")
        div { classes = Seq("component-showcase__title"); text(title) {} }
        summary.foreach { value =>
          div { classes = Seq("component-showcase__summary"); text(value) {} }
        }
      }

      contentHost = div {
        classes = Seq("component-showcase__render")
      }
    }

    renderInto(contentHost) {
      content
    }
  }

  private def renderApiSection(
      title: ReadOnlyProperty[String],
      summary: Option[ReadOnlyProperty[String]]
  )(
      content: AbstractComponent ?=> Cursor ?=> Unit
  )(using parent: AbstractComponent, cursor: Cursor): Unit = {
    var contentHost: Div = null

    vbox {
      classes = Seq("api-section")

      vbox {
        classes = Seq("api-section__header")
        div { classes = Seq("api-section__title"); text(title) {} }
        summary.foreach { value =>
          div { classes = Seq("api-section__summary"); text(value) {} }
        }
      }

      contentHost = div {
        classes = Seq("api-section__content")
      }
    }

    renderInto(contentHost) {
      content
    }
  }
}
