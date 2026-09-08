package app

import app.AppElement.element
import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.classes
import jfx.core.dsl.DslLayer.{child, render}
import jfx.core.dsl.EventDsl.on
import jfx.core.i18n.i18n
import jfx.core.layout.TextComponent.text
import jfx.core.render.Cursor
import jfx.core.state.ReadOnlyProperty
import jfx.core.text.TextValue.asReadOnlyProperty
import org.scalajs.dom

final class PreferenceControls(theme: AppTheme) extends AbstractComponent {
  val tagName = "div"
  override def compose(cursor: Cursor): Unit = render(this, cursor) {
    addClass("preferences")
    element("label") {
      summon[AppElement].setAttribute("for", "design-choice")
      element("span") { classes = Seq("preferences__label"); text(i18n"Design") {} }
      child(new Selection("design-choice", DesignPreferences.designs.toSeq.map(d => d.id -> asReadOnlyProperty(d.name)),
        theme.serverState.design, theme.designProperty, theme.setDesign)) {}
    }
    element("label") {
      summon[AppElement].setAttribute("for", "scheme-choice")
      element("span") { classes = Seq("preferences__label"); text(i18n"Appearance") {} }
      val options = Seq("light" -> asReadOnlyProperty(i18n"Light"), "dark" -> asReadOnlyProperty(i18n"Dark"))
      child(new Selection("scheme-choice", options, theme.serverState.colorScheme,
        theme.modeProperty.map(_.value), value => theme.set(AppTheme.Mode.parse(value).getOrElse(AppTheme.Mode.Light)))) {}
    }
    element("span") {
      classes = Seq("preferences__status")
      summon[AppElement].setAttribute("role", "status")
      val warning = asReadOnlyProperty(i18n"Selection applies to this page only: browser storage is unavailable.")
      text(theme.storageAvailableProperty.flatMap(available => if (available) asReadOnlyProperty("") else warning)) {}
    }
  }

  private final class Selection(id: String, options: Seq[(String, ReadOnlyProperty[String])], initial: String,
      value: ReadOnlyProperty[String], change: String => Unit) extends AbstractComponent {
    val tagName = "select"
    override def compose(cursor: Cursor): Unit = render(this, cursor) {
      setAttribute("id", id)
      setAttribute("disabled", "")
      options.foreach { case (key, label) =>
        element("option") {
          summon[AppElement].setAttribute("value", key)
          if (key == initial) summon[AppElement].setAttribute("selected", "")
          text(label) {}
        }
      }
      on("change") { event => change(event.raw.asInstanceOf[dom.Event].target.asInstanceOf[dom.html.Select].value) }
    }
    override def afterCompose(cursor: Cursor): Unit = if (cursor.isBrowser) {
      removeAttribute("disabled")
      addDisposable(value.observe(v => setProperty("value", v)))
    }
  }
}
