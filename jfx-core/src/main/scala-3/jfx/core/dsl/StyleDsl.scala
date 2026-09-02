package jfx.core.dsl

import jfx.core.component.AbstractComponent
import jfx.core.state.ReadOnlyProperty

trait StyleDsl {

  def setStyle(name: String, value: String): Unit

  def removeStyle(name: String): Unit

}

/** Inline styles written from Scala.
  *
  * Inline styles carry values that are only known at runtime — a measured width, a computed
  * translate, the offset of a virtual list row. Appearance belongs in the component CSS; which
  * system owns what is written down in `npm/scalajs-jfx/README.md`.
  *
  * `css` reads, sets and removes any property by its CSS name, so a property this object does not
  * happen to name needs no change here:
  *
  * {{{
  * style {
  *   css("aspect-ratio", "16 / 9")
  *   width = "240px"
  *   left = offsetProperty          // re-applied whenever the property changes
  *   if (css("aspect-ratio").isEmpty) ...
  * }
  * }}}
  *
  * The named properties below are conveniences on top of `css`: they spell the CSS name once and
  * turn a typo into a compile error. Each one comes as a triple — a getter and two setters, one for
  * a `String` and one for a `ReadOnlyProperty[String]`.
  *
  * The getter carries its own weight twice over. It reads back the inline value that was set — the
  * same scope as the setter, not the computed style, which does not exist during SSR. And it has to
  * be there at all: Scala rewrites `width = v` into `width_=(v)` only when a getter of that name
  * exists, otherwise the call site fails with "Not found: width — did you mean width_=?".
  */
object StyleDsl {

  def style(init: StyleProxy ?=> Unit)(using c: AbstractComponent): Unit = {
    val proxy = new StyleProxy(c.host)
    init(using proxy)
  }

  /** Reads the inline value of any CSS property, empty when it is not set. */
  def css(name: String)(using s: StyleProxy): String =
    s.host.style(name).getOrElse("")

  /** Sets any CSS property by its CSS name. */
  def css(name: String, value: String)(using s: StyleProxy): Unit =
    s.host.setStyle(name, value)

  /** Binds any CSS property to a property; re-applied on every change. */
  def css(name: String, value: ReadOnlyProperty[String])(using
      s: StyleProxy,
      c: AbstractComponent
  ): Unit =
    c.addDisposable(value.observe(s.host.setStyle(name, _)))

  /** Removes any CSS property by its CSS name. */
  def clearCss(name: String)(using s: StyleProxy): Unit =
    s.host.removeStyle(name)

  def boxSizing(using StyleProxy): String            = css("box-sizing")
  def boxSizing_=(v: String)(using StyleProxy): Unit =
    css("box-sizing", v)
  def boxSizing_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("box-sizing", v)

  def width(using StyleProxy): String            = css("width")
  def width_=(v: String)(using StyleProxy): Unit =
    css("width", v)
  def width_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("width", v)

  def height(using StyleProxy): String            = css("height")
  def height_=(v: String)(using StyleProxy): Unit =
    css("height", v)
  def height_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("height", v)

  def transform(using StyleProxy): String            = css("transform")
  def transform_=(v: String)(using StyleProxy): Unit =
    css("transform", v)
  def transform_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("transform", v)

  def top(using StyleProxy): String            = css("top")
  def top_=(v: String)(using StyleProxy): Unit =
    css("top", v)
  def top_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("top", v)

  def minHeight(using StyleProxy): String            = css("min-height")
  def minHeight_=(v: String)(using StyleProxy): Unit =
    css("min-height", v)
  def minHeight_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("min-height", v)

  def minWidth(using StyleProxy): String            = css("min-width")
  def minWidth_=(v: String)(using StyleProxy): Unit =
    css("min-width", v)
  def minWidth_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("min-width", v)

  def maxHeight(using StyleProxy): String            = css("max-height")
  def maxHeight_=(v: String)(using StyleProxy): Unit =
    css("max-height", v)
  def maxHeight_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("max-height", v)

  def maxWidth(using StyleProxy): String            = css("max-width")
  def maxWidth_=(v: String)(using StyleProxy): Unit =
    css("max-width", v)
  def maxWidth_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("max-width", v)

  def marginTop(using StyleProxy): String            = css("margin-top")
  def marginTop_=(v: String)(using StyleProxy): Unit =
    css("margin-top", v)
  def marginTop_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("margin-top", v)

  def marginRight(using StyleProxy): String            = css("margin-right")
  def marginRight_=(v: String)(using StyleProxy): Unit =
    css("margin-right", v)
  def marginRight_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("margin-right", v)

  def marginBottom(using StyleProxy): String            = css("margin-bottom")
  def marginBottom_=(v: String)(using StyleProxy): Unit =
    css("margin-bottom", v)
  def marginBottom_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("margin-bottom", v)

  def marginLeft(using StyleProxy): String            = css("margin-left")
  def marginLeft_=(v: String)(using StyleProxy): Unit =
    css("margin-left", v)
  def marginLeft_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("margin-left", v)

  def display(using StyleProxy): String            = css("display")
  def display_=(v: String)(using StyleProxy): Unit =
    css("display", v)
  def display_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("display", v)

  def flexDirection(using StyleProxy): String            = css("flex-direction")
  def flexDirection_=(v: String)(using StyleProxy): Unit =
    css("flex-direction", v)
  def flexDirection_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("flex-direction", v)

  def alignItems(using StyleProxy): String            = css("align-items")
  def alignItems_=(v: String)(using StyleProxy): Unit =
    css("align-items", v)
  def alignItems_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("align-items", v)

  def justifyContent(using StyleProxy): String            = css("justify-content")
  def justifyContent_=(v: String)(using StyleProxy): Unit =
    css("justify-content", v)
  def justifyContent_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("justify-content", v)

  def flex(using StyleProxy): String            = css("flex")
  def flex_=(v: String)(using StyleProxy): Unit =
    css("flex", v)
  def flex_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("flex", v)

  def gap(using StyleProxy): String            = css("gap")
  def gap_=(v: String)(using StyleProxy): Unit =
    css("gap", v)
  def gap_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("gap", v)

  def padding(using StyleProxy): String            = css("padding")
  def padding_=(v: String)(using StyleProxy): Unit =
    css("padding", v)
  def padding_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("padding", v)

  def background(using StyleProxy): String            = css("background")
  def background_=(v: String)(using StyleProxy): Unit =
    css("background", v)
  def background_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("background", v)

  def backgroundColor(using StyleProxy): String            = css("background-color")
  def backgroundColor_=(v: String)(using StyleProxy): Unit =
    css("background-color", v)
  def backgroundColor_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("background-color", v)

  def borderRadius(using StyleProxy): String            = css("border-radius")
  def borderRadius_=(v: String)(using StyleProxy): Unit =
    css("border-radius", v)
  def borderRadius_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("border-radius", v)

  def border(using StyleProxy): String            = css("border")
  def border_=(v: String)(using StyleProxy): Unit =
    css("border", v)
  def border_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("border", v)

  def borderBottom(using StyleProxy): String            = css("border-bottom")
  def borderBottom_=(v: String)(using StyleProxy): Unit =
    css("border-bottom", v)
  def borderBottom_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("border-bottom", v)

  def borderTop(using StyleProxy): String            = css("border-top")
  def borderTop_=(v: String)(using StyleProxy): Unit =
    css("border-top", v)
  def borderTop_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("border-top", v)

  def borderLeft(using StyleProxy): String            = css("border-left")
  def borderLeft_=(v: String)(using StyleProxy): Unit =
    css("border-left", v)
  def borderLeft_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("border-left", v)

  def borderRight(using StyleProxy): String            = css("border-right")
  def borderRight_=(v: String)(using StyleProxy): Unit =
    css("border-right", v)
  def borderRight_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("border-right", v)

  def fontWeight(using StyleProxy): String            = css("font-weight")
  def fontWeight_=(v: String)(using StyleProxy): Unit =
    css("font-weight", v)
  def fontWeight_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("font-weight", v)

  def textAlign(using StyleProxy): String            = css("text-align")
  def textAlign_=(v: String)(using StyleProxy): Unit =
    css("text-align", v)
  def textAlign_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("text-align", v)

  def color(using StyleProxy): String            = css("color")
  def color_=(v: String)(using StyleProxy): Unit =
    css("color", v)
  def color_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("color", v)

  def fontSize(using StyleProxy): String            = css("font-size")
  def fontSize_=(v: String)(using StyleProxy): Unit =
    css("font-size", v)
  def fontSize_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("font-size", v)

  def opacity(using StyleProxy): String            = css("opacity")
  def opacity_=(v: String)(using StyleProxy): Unit =
    css("opacity", v)
  def opacity_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("opacity", v)

  def position(using StyleProxy): String            = css("position")
  def position_=(v: String)(using StyleProxy): Unit =
    css("position", v)
  def position_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("position", v)

  def right(using StyleProxy): String            = css("right")
  def right_=(v: String)(using StyleProxy): Unit =
    css("right", v)
  def right_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("right", v)

  def bottom(using StyleProxy): String            = css("bottom")
  def bottom_=(v: String)(using StyleProxy): Unit =
    css("bottom", v)
  def bottom_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("bottom", v)

  def left(using StyleProxy): String            = css("left")
  def left_=(v: String)(using StyleProxy): Unit =
    css("left", v)
  def left_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("left", v)

  def zIndex(using StyleProxy): String            = css("z-index")
  def zIndex_=(v: String)(using StyleProxy): Unit =
    css("z-index", v)
  def zIndex_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("z-index", v)

  def overflow(using StyleProxy): String            = css("overflow")
  def overflow_=(v: String)(using StyleProxy): Unit =
    css("overflow", v)
  def overflow_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("overflow", v)

  def overflowY(using StyleProxy): String            = css("overflow-y")
  def overflowY_=(v: String)(using StyleProxy): Unit =
    css("overflow-y", v)
  def overflowY_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("overflow-y", v)

  def overflowX(using StyleProxy): String            = css("overflow-x")
  def overflowX_=(v: String)(using StyleProxy): Unit =
    css("overflow-x", v)
  def overflowX_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("overflow-x", v)

  def boxShadow(using StyleProxy): String            = css("box-shadow")
  def boxShadow_=(v: String)(using StyleProxy): Unit =
    css("box-shadow", v)
  def boxShadow_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("box-shadow", v)

  def objectFit(using StyleProxy): String            = css("object-fit")
  def objectFit_=(v: String)(using StyleProxy): Unit =
    css("object-fit", v)
  def objectFit_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("object-fit", v)

  def objectPosition(using StyleProxy): String            = css("object-position")
  def objectPosition_=(v: String)(using StyleProxy): Unit =
    css("object-position", v)
  def objectPosition_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("object-position", v)

  def cursor(using StyleProxy): String            = css("cursor")
  def cursor_=(v: String)(using StyleProxy): Unit =
    css("cursor", v)
  def cursor_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("cursor", v)

  def font(using StyleProxy): String            = css("font")
  def font_=(v: String)(using StyleProxy): Unit =
    css("font", v)
  def font_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("font", v)

  def appearance(using StyleProxy): String            = css("appearance")
  def appearance_=(v: String)(using StyleProxy): Unit =
    css("appearance", v)
  def appearance_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("appearance", v)

  def pointerEvents(using StyleProxy): String            = css("pointer-events")
  def pointerEvents_=(v: String)(using StyleProxy): Unit =
    css("pointer-events", v)
  def pointerEvents_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("pointer-events", v)

  def flexWrap(using StyleProxy): String            = css("flex-wrap")
  def flexWrap_=(v: String)(using StyleProxy): Unit =
    css("flex-wrap", v)
  def flexWrap_=(v: ReadOnlyProperty[String])(using StyleProxy, AbstractComponent): Unit =
    css("flex-wrap", v)
}
