package jfx.core.layout

import jfx.core.component.AbstractComponent
import jfx.core.dsl.DslLayer
import jfx.core.render.Cursor
import jfx.core.state.ReadOnlyProperty

final class Image extends AbstractComponent {
  val tagName = "img"

  def src: String =
    host.attribute("src").getOrElse("")

  def src_=(value: String): Unit =
    host.setAttribute("src", value)

  def src_=(value: ReadOnlyProperty[String]): Unit =
    addDisposable(value.observe(next => host.setAttribute("src", Option(next).getOrElse(""))))

  def alt: String =
    host.attribute("alt").getOrElse("")

  def alt_=(value: String): Unit =
    host.setAttribute("alt", value)
}

object Image {
  def image(
      body: Image ?=> Cursor ?=> Unit = {}
  )(using AbstractComponent, Cursor): Image =
    DslLayer.child(new Image()) {
      body
    }

  def src_=(value: String)(using image: Image): Unit =
    image.src_=(value)

  def src_=(value: ReadOnlyProperty[String])(using image: Image): Unit =
    image.src_=(value)

  def src(using image: Image): String =
    image.src

  def alt_=(value: String)(using image: Image): Unit =
    image.alt_=(value)

  def alt(using image: Image): String =
    image.alt
}
