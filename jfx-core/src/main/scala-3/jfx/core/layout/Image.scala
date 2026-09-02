package jfx.core.layout

import jfx.core.component.AbstractComponent
import jfx.core.dsl.DslLayer
import jfx.core.render.Cursor
import jfx.core.state.ReadOnlyProperty
import jfx.core.text.TextValue

final class Image extends AbstractComponent {
  val tagName = "img"

  def src: String =
    attribute("src").getOrElse("")

  def src_=(value: String): Unit =
    setSource(value)

  def src_=(value: ReadOnlyProperty[String]): Unit =
    addDisposable(value.observe(setSource))

  def alt: String =
    attribute("alt").getOrElse("")

  def alt_=[T](value: T)(using textValue: TextValue[T]): Unit = {
    val altProperty = textValue.asReadOnlyProperty(value)(using this)
    addDisposable(altProperty.observe(next => setAttribute("alt", Option(next).getOrElse(""))))
  }

  private def setSource(value: String): Unit =
    Option(value).filter(_.trim.nonEmpty) match {
      case Some(source) => setAttribute("src", source)
      case None         => removeAttribute("src")
    }
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

  def alt_=[T](value: T)(using image: Image, textValue: TextValue[T]): Unit =
    image.alt_=(value)

  def alt(using image: Image): String =
    image.alt
}
