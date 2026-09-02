package jfx.core.layout

import jfx.core.component.AbstractComponent
import jfx.core.dsl.DslLayer
import jfx.core.render.Cursor
import jfx.core.text.TextValue

final class Anchor extends AbstractComponent {
  val tagName = "a"

  def href: String =
    attribute("href").getOrElse("#")

  def href_=(value: String): Unit =
    setAttribute("href", Option(value).getOrElse("#"))

  def target: String =
    attribute("target").getOrElse("")

  def target_=(value: String): Unit =
    setAttribute("target", value)

  def rel: String =
    attribute("rel").getOrElse("")

  def rel_=(value: String): Unit =
    setAttribute("rel", value)
}

object Anchor {
  def anchor()(
      body: Anchor ?=> Cursor ?=> Unit
  )(using AbstractComponent, Cursor): Anchor =
    DslLayer.child(new Anchor()) {
      body
    }

  def anchor[T](
      label: T
  )(body: Anchor ?=> Cursor ?=> Unit = {})(using
      parent: AbstractComponent,
      cursor: Cursor,
      textValue: TextValue[T]
  ): Anchor = {
    val link = new Anchor()

    DslLayer.child(link) {
      TextComponent.text(label) {}
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
}
