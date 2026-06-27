package jfx.core.dsl

import jfx.core.component.AbstractComponent
import jfx.core.render.HostElement
import jfx.core.state.ReadOnlyProperty

class StyleProxy(val host: HostElement)

object StyleDsl {
  def style(init: StyleProxy ?=> Unit)(using c: AbstractComponent): Unit = {
    val proxy = new StyleProxy(c.host)
    init(using proxy)
  }

  def boxSizing(using s: StyleProxy): String            = ""
  def boxSizing_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("box-sizing", v)

  def width(using s: StyleProxy): String            = ""
  def width_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("width", v)
  def width_=(v: ReadOnlyProperty[String])(using s: StyleProxy, c: AbstractComponent): Unit =
    c.addDisposable(v.observe(s.host.setStyle("width", _)))

  def height(using s: StyleProxy): String            = ""
  def height_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("height", v)
  def height_=(v: ReadOnlyProperty[String])(using s: StyleProxy, c: AbstractComponent): Unit =
    c.addDisposable(v.observe(s.host.setStyle("height", _)))

  def transform(using s: StyleProxy): String            = ""
  def transform_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("transform", v)
  def transform_=(v: ReadOnlyProperty[String])(using s: StyleProxy, c: AbstractComponent): Unit =
    c.addDisposable(v.observe(s.host.setStyle("transform", _)))

  def top(using s: StyleProxy): String            = ""
  def top_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("top", v)
  def top_=(v: ReadOnlyProperty[String])(using s: StyleProxy, c: AbstractComponent): Unit =
    c.addDisposable(v.observe(s.host.setStyle("top", _)))

  def minHeight(using s: StyleProxy): String            = ""
  def minHeight_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("min-height", v)
  def minHeight_=(v: ReadOnlyProperty[String])(using s: StyleProxy, c: AbstractComponent): Unit =
    c.addDisposable(v.observe(s.host.setStyle("min-height", _)))

  def minWidth(using s: StyleProxy): String            = ""
  def minWidth_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("min-width", v)
  def minWidth_=(v: ReadOnlyProperty[String])(using s: StyleProxy, c: AbstractComponent): Unit =
    c.addDisposable(v.observe(s.host.setStyle("min-width", _)))

  def maxHeight(using s: StyleProxy): String            = ""
  def maxHeight_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("max-height", v)
  def maxHeight_=(v: ReadOnlyProperty[String])(using s: StyleProxy, c: AbstractComponent): Unit =
    c.addDisposable(v.observe(s.host.setStyle("max-height", _)))

  def maxWidth(using s: StyleProxy): String            = ""
  def maxWidth_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("max-width", v)
  def maxWidth_=(v: ReadOnlyProperty[String])(using s: StyleProxy, c: AbstractComponent): Unit =
    c.addDisposable(v.observe(s.host.setStyle("max-width", _)))

  def marginTop(using s: StyleProxy): String            = ""
  def marginTop_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("margin-top", v)
  def marginTop_=(v: ReadOnlyProperty[String])(using s: StyleProxy, c: AbstractComponent): Unit =
    c.addDisposable(v.observe(s.host.setStyle("margin-top", _)))

  def marginRight(using s: StyleProxy): String            = ""
  def marginRight_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("margin-right", v)

  def marginBottom(using s: StyleProxy): String            = ""
  def marginBottom_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("margin-bottom", v)

  def marginLeft(using s: StyleProxy): String            = ""
  def marginLeft_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("margin-left", v)

  def display(using s: StyleProxy): String            = ""
  def display_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("display", v)
  def display_=(v: ReadOnlyProperty[String])(using s: StyleProxy, c: AbstractComponent): Unit =
    c.addDisposable(v.observe(s.host.setStyle("display", _)))

  def flexDirection(using s: StyleProxy): String            = ""
  def flexDirection_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("flex-direction", v)

  def alignItems(using s: StyleProxy): String            = ""
  def alignItems_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("align-items", v)

  def justifyContent(using s: StyleProxy): String            = ""
  def justifyContent_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("justify-content", v)

  def flex(using s: StyleProxy): String            = ""
  def flex_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("flex", v)

  def gap(using s: StyleProxy): String            = ""
  def gap_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("gap", v)

  def padding(using s: StyleProxy): String            = ""
  def padding_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("padding", v)

  def background(using s: StyleProxy): String            = ""
  def background_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("background", v)

  def backgroundColor(using s: StyleProxy): String = ""
  def backgroundColor_=(v: String)(using s: StyleProxy): Unit =
    s.host.setStyle("background-color", v)

  def borderRadius(using s: StyleProxy): String            = ""
  def borderRadius_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("border-radius", v)

  def border(using s: StyleProxy): String            = ""
  def border_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("border", v)

  def borderBottom(using s: StyleProxy): String            = ""
  def borderBottom_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("border-bottom", v)

  def borderTop(using s: StyleProxy): String            = ""
  def borderTop_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("border-top", v)

  def borderLeft(using s: StyleProxy): String            = ""
  def borderLeft_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("border-left", v)

  def borderRight(using s: StyleProxy): String            = ""
  def borderRight_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("border-right", v)

  def fontWeight(using s: StyleProxy): String            = ""
  def fontWeight_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("font-weight", v)

  def textAlign(using s: StyleProxy): String            = ""
  def textAlign_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("text-align", v)

  def color(using s: StyleProxy): String            = ""
  def color_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("color", v)

  def fontSize(using s: StyleProxy): String            = ""
  def fontSize_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("font-size", v)

  def opacity(using s: StyleProxy): String            = ""
  def opacity_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("opacity", v)

  def position(using s: StyleProxy): String            = ""
  def position_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("position", v)

  def right(using s: StyleProxy): String            = ""
  def right_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("right", v)

  def bottom(using s: StyleProxy): String            = ""
  def bottom_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("bottom", v)

  def left(using s: StyleProxy): String            = ""
  def left_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("left", v)
  def left_=(v: ReadOnlyProperty[String])(using s: StyleProxy, c: AbstractComponent): Unit =
    c.addDisposable(v.observe(s.host.setStyle("left", _)))

  def zIndex(using s: StyleProxy): String            = ""
  def zIndex_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("z-index", v)
  def zIndex_=(v: ReadOnlyProperty[String])(using s: StyleProxy, c: AbstractComponent): Unit =
    c.addDisposable(v.observe(s.host.setStyle("z-index", _)))

  def overflow(using s: StyleProxy): String            = ""
  def overflow_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("overflow", v)

  def overflowY(using s: StyleProxy): String            = ""
  def overflowY_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("overflow-y", v)

  def overflowX(using s: StyleProxy): String            = ""
  def overflowX_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("overflow-x", v)

  def boxShadow(using s: StyleProxy): String            = ""
  def boxShadow_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("box-shadow", v)

  def objectFit(using s: StyleProxy): String            = ""
  def objectFit_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("object-fit", v)

  def objectPosition(using s: StyleProxy): String            = ""
  def objectPosition_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("object-position", v)

  def cursor(using s: StyleProxy): String            = ""
  def cursor_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("cursor", v)

  def pointerEvents(using s: StyleProxy): String            = ""
  def pointerEvents_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("pointer-events", v)

  def flexWrap(using s: StyleProxy): String            = ""
  def flexWrap_=(v: String)(using s: StyleProxy): Unit = s.host.setStyle("flex-wrap", v)
}
