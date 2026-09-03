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
    bindStringAttribute("src", value)

  def alt: String =
    attribute("alt").getOrElse("")

  def alt_=[T](value: T)(using textValue: TextValue[T]): Unit = {
    val altProperty = textValue.asReadOnlyProperty(value)(using this)
    bindStringAttribute("alt", altProperty, removeWhenEmpty = false)
  }

  def loading: String =
    attribute("loading").getOrElse("")

  def loading_=(value: String): Unit =
    setStringAttribute("loading", value)

  def loading_=(value: ReadOnlyProperty[String]): Unit =
    bindStringAttribute("loading", value)

  def srcset: String =
    attribute("srcset").getOrElse("")

  def srcset_=(value: String): Unit =
    setStringAttribute("srcset", value)

  def srcset_=(value: ReadOnlyProperty[String]): Unit =
    bindStringAttribute("srcset", value)

  def width: Int =
    attribute("width").flatMap(_.toIntOption).getOrElse(0)

  def width_=(value: Int): Unit =
    setDimension("width", value)

  def width_=(value: ReadOnlyProperty[Int]): Unit =
    addDisposable(value.observe(setDimension("width", _)))

  def height: Int =
    attribute("height").flatMap(_.toIntOption).getOrElse(0)

  def height_=(value: Int): Unit =
    setDimension("height", value)

  def height_=(value: ReadOnlyProperty[Int]): Unit =
    addDisposable(value.observe(setDimension("height", _)))

  private def setSource(value: String): Unit =
    setStringAttribute("src", value)

  private def bindStringAttribute(
      name: String,
      value: ReadOnlyProperty[String],
      removeWhenEmpty: Boolean = true
  ): Unit =
    addDisposable(value.observe(setStringAttribute(name, _, removeWhenEmpty)))

  private def setStringAttribute(
      name: String,
      value: String,
      removeWhenEmpty: Boolean = true
  ): Unit =
    if (!removeWhenEmpty) {
      setAttribute(name, Option(value).getOrElse(""))
    } else {
      Option(value).filter(_.trim.nonEmpty) match {
        case Some(next) => setAttribute(name, next)
        case None       => removeAttribute(name)
      }
    }

  private def setDimension(name: String, value: Int): Unit =
    if (value > 0) setAttribute(name, value.toString)
    else removeAttribute(name)
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

  def loading_=(value: String)(using image: Image): Unit =
    image.loading_=(value)

  def loading_=(value: ReadOnlyProperty[String])(using image: Image): Unit =
    image.loading_=(value)

  def loading(using image: Image): String =
    image.loading

  def srcset_=(value: String)(using image: Image): Unit =
    image.srcset_=(value)

  def srcset_=(value: ReadOnlyProperty[String])(using image: Image): Unit =
    image.srcset_=(value)

  def srcset(using image: Image): String =
    image.srcset

  def intrinsicWidth_=(value: Int)(using image: Image): Unit =
    image.width_=(value)

  def intrinsicWidth_=(value: ReadOnlyProperty[Int])(using image: Image): Unit =
    image.width_=(value)

  def intrinsicWidth(using image: Image): Int =
    image.width

  def intrinsicHeight_=(value: Int)(using image: Image): Unit =
    image.height_=(value)

  def intrinsicHeight_=(value: ReadOnlyProperty[Int])(using image: Image): Unit =
    image.height_=(value)

  def intrinsicHeight(using image: Image): Int =
    image.height
}
