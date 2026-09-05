package jfx.editor

import jfx.core.component.AbstractComponent
import jfx.core.context.UrlScope
import jfx.core.dsl.AttributeDsl.{setAttribute as setDslAttribute}
import jfx.core.dsl.ClassDsl.classes
import jfx.core.dsl.DslLayer.render
import jfx.core.dsl.EventDsl.{on, onClick}
import jfx.core.layout.Div.div
import jfx.core.layout.TextComponent.text
import jfx.core.render.Cursor
import jfx.core.state.Property
import jfx.core.statement.DynamicComponentRenderer.dynamic
import org.scalajs.dom
import org.scalajs.dom.HTMLTextAreaElement

/** The readonly SSR/no-JavaScript presentation owned by [[Editor]]. */
private final class MarkdownReadonly(valueProperty: Property[String]) extends AbstractComponent {
  override val tagName: String = "div"

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      classes = Seq("jfx-editor__readonly")
      div {
        classes = Seq("jfx-editor__preview", "jfx-editor-readonly")
        setDslAttribute("aria-readonly", "true")
        dynamic(valueProperty.map[AbstractComponent](value => new MarkdownRenderer(value)))
      }
    }
}

/** The editable SSR/no-JavaScript presentation owned by [[Editor]]. */
private final class MarkdownTextArea(
    name: String,
    valueProperty: Property[String],
    placeholderProperty: Property[String],
    onMarkdownChanged: String => Unit,
    onFocusChanged: Boolean => Unit
) extends AbstractComponent {
  override val tagName: String = "textarea"

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      classes = Seq("jfx-editor__markdown-textarea")
      setDslAttribute("name", name)
      setDslAttribute("aria-label", name)
      setDslAttribute("aria-multiline", "true")
      setDslAttribute("spellcheck", "true")
      Option(placeholderProperty.get).filter(_.nonEmpty).foreach(setDslAttribute("placeholder", _))

      text(valueProperty) {}

      on("input") { event =>
        event.raw match {
          case domEvent: dom.Event =>
            domEvent.target match {
              case textarea: HTMLTextAreaElement => onMarkdownChanged(textarea.value)
              case _                             => ()
            }
          case _ => ()
        }
      }
      on("focus") { _ => onFocusChanged(true) }
      on("blur") { _ => onFocusChanged(false) }

      if (cursor.isBrowser)
        addDisposable(valueProperty.observe(value => setProperty("value", value)))
    }
}

/** The ordinary link used to change editor mode with or without JavaScript. */
private[editor] final class MarkdownModeLink(
    url: String,
    label: String,
    readonly: Boolean,
    onActivate: () => Unit
) extends AbstractComponent {
  override val tagName: String = "a"

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      val safeUrl = MarkdownSecurity.safeLinkUrl(url)
      classes = Seq("jfx-editor__mode-toggle", "jfx-editor__edit-link") ++
        Option.when(readonly)("jfx-editor__readonly-link")
      setDslAttribute("href", safeUrl)
      text(label) {}
      if (cursor.isBrowser && isInternalDestination(safeUrl))
        onClick { event =>
          event.preventDefault()
          onActivate()
          UrlScope.current(using this) match {
            case Some(scope) => scope.navigate(safeUrl, replace = false)
            case None        => dom.window.history.pushState(null, "", safeUrl)
          }
        }
    }

  private def isInternalDestination(destination: String): Boolean =
    (destination.startsWith("/") && !destination.startsWith("//")) ||
      destination.startsWith("?") ||
      destination.startsWith("#")
}
