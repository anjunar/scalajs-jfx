package jfx.core.layout

import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.DslLayerTwo
import jfx.core.render.Cursor
import jfx.core.state.ReadOnlyProperty
import jfx.core.state.Property
import jfx.core.text.TextValue

class Button extends AbstractComponent {
  val tagName = "button"

  private val labelProperty = Property("")

  def buttonType(value: String): Unit =
    host.setAttribute("type", value)

  def label(value: String): Unit =
    labelProperty.set(Option(value).getOrElse(""))

  def label(value: ReadOnlyProperty[String]): Unit =
    addDisposable(value.observe(labelProperty.set))

  override def compose(cursor: Cursor): Unit =
    Runtime.mount(TextComponent.bind(labelProperty), cursor, Some(this))
}

object Button {
  def button[T](
      label: T
  )(body: Button ?=> Cursor ?=> Unit = {})(using
      AbstractComponent,
      Cursor,
      TextValue[T]
  ): Button = {
    val buttonComponent = new Button()
    DslLayerTwo.child(buttonComponent) {
      label_=(label)(using buttonComponent, summon[TextValue[T]], summon[AbstractComponent])
      body
    }
  }

  def buttonType(value: String)(using button: Button): Unit =
    button.buttonType(value)

  def label_=(value: String)(using button: Button): Unit =
    button.label(value)

  def label_=(value: ReadOnlyProperty[String])(using button: Button): Unit =
    button.label(value)

  def label_=[T](value: T)(using
      button: Button,
      textValue: TextValue[T],
      component: AbstractComponent
  ): Unit =
    button.label(textValue.asReadOnlyProperty(value))
}
