package app.components

import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.DslLayerTwo
import jfx.core.render.Cursor
import jfx.core.state.{Property, ReadOnlyProperty}
import jfx.core.layout.TextComponent
import jfx.core.text.TextValue

final class Anchor extends AbstractComponent {
  val tagName = "a"

  private val labelProperty =
    Property("")

  def href: String =
    host.attribute("href").getOrElse("#")

  def href_=(value: String): Unit =
    host.setAttribute("href", Option(value).getOrElse("#"))

  def target: String =
    host.attribute("target").getOrElse("")

  def target_=(value: String): Unit =
    host.setAttribute("target", value)

  def rel: String =
    host.attribute("rel").getOrElse("")

  def rel_=(value: String): Unit =
    host.setAttribute("rel", value)

  def label(value: String): Unit =
    labelProperty.set(Option(value).getOrElse(""))

  def label(value: ReadOnlyProperty[String]): Unit =
    addDisposable(value.observe(labelProperty.set))

  override def compose(cursor: Cursor): Unit =
    Runtime.mount(TextComponent.bind(labelProperty), cursor, Some(this))
}

object Anchor {
  def anchor[T](
      label: T
  )(body: Anchor ?=> Cursor ?=> Unit = {})(using
      AbstractComponent,
      Cursor,
      TextValue[T]
  ): Anchor = {
    val link = new Anchor()

    DslLayerTwo.child(link) {
      label_=(label)(using link, summon[TextValue[T]], summon[AbstractComponent])
      body
    }
  }

  def href_=(value: String)(using anchor: Anchor): Unit =
    anchor.href_=(value)

  def href(using anchor: Anchor): String =
    anchor.href

  def target_=(value: String)(using anchor: Anchor): Unit =
    anchor.target_=(value)

  def target(using anchor: Anchor): String =
    anchor.target

  def rel_=(value: String)(using anchor: Anchor): Unit =
    anchor.rel_=(value)

  def rel(using anchor: Anchor): String =
    anchor.rel

  def label_=(value: String)(using anchor: Anchor): Unit =
    anchor.label(value)

  def label_=(value: ReadOnlyProperty[String])(using anchor: Anchor): Unit =
    anchor.label(value)

  def label_=[T](value: T)(using
      anchor: Anchor,
      textValue: TextValue[T],
      component: AbstractComponent
  ): Unit =
    anchor.label(textValue.asReadOnlyProperty(value))
}

final class Image extends AbstractComponent {
  val tagName = "img"

  def src: String =
    host.attribute("src").getOrElse("")

  def src_=(value: String): Unit =
    host.setAttribute("src", value)

  def alt: String =
    host.attribute("alt").getOrElse("")

  def alt_=(value: String): Unit =
    host.setAttribute("alt", value)
}

object Image {
  def image(
      body: Image ?=> Cursor ?=> Unit = {}
  )(using AbstractComponent, Cursor): Image =
    DslLayerTwo.child(new Image()) {
      body
    }

  def src_=(value: String)(using image: Image): Unit =
    image.src_=(value)

  def src(using image: Image): String =
    image.src

  def alt_=(value: String)(using image: Image): Unit =
    image.alt_=(value)

  def alt(using image: Image): String =
    image.alt
}
