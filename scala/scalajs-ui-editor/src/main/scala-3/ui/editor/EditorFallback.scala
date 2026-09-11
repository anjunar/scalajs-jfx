package ui.editor

import ui.core.component.AbstractComponent
import ui.core.context.UrlScope
import ui.core.dsl.AttributeDsl.{setAttribute as setDslAttribute}
import ui.core.dsl.ClassDsl.classes
import ui.core.dsl.DslLayer.render
import ui.core.dsl.EventDsl.{on, onClick}
import ui.core.layout.Div.div
import ui.core.layout.TextComponent.text
import ui.core.render.Cursor
import ui.core.state.Property
import ui.core.statement.DynamicComponentRenderer.dynamic
import org.scalajs.dom
import org.scalajs.dom.HTMLTextAreaElement

/** The readonly SSR/no-JavaScript presentation owned by [[Editor]]. */
private final class MarkdownReadonly(valueProperty: Property[String], policy: MediaUrlPolicy)
    extends AbstractComponent {
  override val tagName: String = "div"

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      classes = Seq("scalajs-ui-editor__readonly")
      div {
        classes = Seq("scalajs-ui-editor__preview", "scalajs-ui-editor-readonly")
        setDslAttribute("aria-readonly", "true")
        dynamic(valueProperty.map[AbstractComponent](value => new MarkdownRenderer(value, policy)))
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
      classes = Seq("scalajs-ui-editor__markdown-textarea")
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
        // The text child already initializes the value. An eager property write would erase
        // edits made to the server-rendered textarea before hydration.
        addDisposable(valueProperty.observeWithoutInitial(value => setProperty("value", value)))
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
      classes = Seq("scalajs-ui-editor__mode-toggle", "scalajs-ui-editor__edit-link") ++
        Option.when(readonly)("scalajs-ui-editor__readonly-link")
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
