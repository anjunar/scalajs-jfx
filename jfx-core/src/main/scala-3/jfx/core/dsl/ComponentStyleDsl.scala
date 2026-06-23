package jfx.core.dsl

import jfx.core.component.AbstractComponent
import jfx.core.render.HostElement
import jfx.core.state.ReadOnlyProperty

trait ComponentStyleDsl {

  def style(body: ComponentStyleDsl.StyleScope ?=> Unit)(using component: AbstractComponent): Unit = {
    given ComponentStyleDsl.StyleScope = ComponentStyleDsl.StyleScope(component.host)
    body
  }

  protected final def bindStyle(name: String, value: ReadOnlyProperty[String])(using scope: ComponentStyleDsl.StyleScope, component: AbstractComponent): Unit =
    component.addDisposable(value.observe(scope.host.setStyle(name, _)))

  def display(using scope: ComponentStyleDsl.StyleScope): String = ""
  def display_=(value: String)(using scope: ComponentStyleDsl.StyleScope): Unit =
    scope.host.setStyle("display", value)
  def display_=(value: ReadOnlyProperty[String])(using scope: ComponentStyleDsl.StyleScope, component: AbstractComponent): Unit =
    bindStyle("display", value)

  def width(using scope: ComponentStyleDsl.StyleScope): String = ""
  def width_=(value: String)(using scope: ComponentStyleDsl.StyleScope): Unit =
    scope.host.setStyle("width", value)
  def width_=(value: ReadOnlyProperty[String])(using scope: ComponentStyleDsl.StyleScope, component: AbstractComponent): Unit =
    bindStyle("width", value)

  def height(using scope: ComponentStyleDsl.StyleScope): String = ""
  def height_=(value: String)(using scope: ComponentStyleDsl.StyleScope): Unit =
    scope.host.setStyle("height", value)
  def height_=(value: ReadOnlyProperty[String])(using scope: ComponentStyleDsl.StyleScope, component: AbstractComponent): Unit =
    bindStyle("height", value)

  def minWidth(using scope: ComponentStyleDsl.StyleScope): String = ""
  def minWidth_=(value: String)(using scope: ComponentStyleDsl.StyleScope): Unit =
    scope.host.setStyle("min-width", value)
  def minWidth_=(value: ReadOnlyProperty[String])(using scope: ComponentStyleDsl.StyleScope, component: AbstractComponent): Unit =
    bindStyle("min-width", value)

  def minHeight(using scope: ComponentStyleDsl.StyleScope): String = ""
  def minHeight_=(value: String)(using scope: ComponentStyleDsl.StyleScope): Unit =
    scope.host.setStyle("min-height", value)
  def minHeight_=(value: ReadOnlyProperty[String])(using scope: ComponentStyleDsl.StyleScope, component: AbstractComponent): Unit =
    bindStyle("min-height", value)

  def maxWidth(using scope: ComponentStyleDsl.StyleScope): String = ""
  def maxWidth_=(value: String)(using scope: ComponentStyleDsl.StyleScope): Unit =
    scope.host.setStyle("max-width", value)
  def maxWidth_=(value: ReadOnlyProperty[String])(using scope: ComponentStyleDsl.StyleScope, component: AbstractComponent): Unit =
    bindStyle("max-width", value)

  def flex(using scope: ComponentStyleDsl.StyleScope): String = ""
  def flex_=(value: String)(using scope: ComponentStyleDsl.StyleScope): Unit =
    scope.host.setStyle("flex", value)
  def flex_=(value: ReadOnlyProperty[String])(using scope: ComponentStyleDsl.StyleScope, component: AbstractComponent): Unit =
    bindStyle("flex", value)

  def flexDirection(using scope: ComponentStyleDsl.StyleScope): String = ""
  def flexDirection_=(value: String)(using scope: ComponentStyleDsl.StyleScope): Unit =
    scope.host.setStyle("flex-direction", value)
  def flexDirection_=(value: ReadOnlyProperty[String])(using scope: ComponentStyleDsl.StyleScope, component: AbstractComponent): Unit =
    bindStyle("flex-direction", value)

  def position(using scope: ComponentStyleDsl.StyleScope): String = ""
  def position_=(value: String)(using scope: ComponentStyleDsl.StyleScope): Unit =
    scope.host.setStyle("position", value)
  def position_=(value: ReadOnlyProperty[String])(using scope: ComponentStyleDsl.StyleScope, component: AbstractComponent): Unit =
    bindStyle("position", value)

  def top(using scope: ComponentStyleDsl.StyleScope): String = ""
  def top_=(value: String)(using scope: ComponentStyleDsl.StyleScope): Unit =
    scope.host.setStyle("top", value)
  def top_=(value: ReadOnlyProperty[String])(using scope: ComponentStyleDsl.StyleScope, component: AbstractComponent): Unit =
    bindStyle("top", value)

  def right(using scope: ComponentStyleDsl.StyleScope): String = ""
  def right_=(value: String)(using scope: ComponentStyleDsl.StyleScope): Unit =
    scope.host.setStyle("right", value)
  def right_=(value: ReadOnlyProperty[String])(using scope: ComponentStyleDsl.StyleScope, component: AbstractComponent): Unit =
    bindStyle("right", value)

  def bottom(using scope: ComponentStyleDsl.StyleScope): String = ""
  def bottom_=(value: String)(using scope: ComponentStyleDsl.StyleScope): Unit =
    scope.host.setStyle("bottom", value)
  def bottom_=(value: ReadOnlyProperty[String])(using scope: ComponentStyleDsl.StyleScope, component: AbstractComponent): Unit =
    bindStyle("bottom", value)

  def left(using scope: ComponentStyleDsl.StyleScope): String = ""
  def left_=(value: String)(using scope: ComponentStyleDsl.StyleScope): Unit =
    scope.host.setStyle("left", value)
  def left_=(value: ReadOnlyProperty[String])(using scope: ComponentStyleDsl.StyleScope, component: AbstractComponent): Unit =
    bindStyle("left", value)

  def overflow(using scope: ComponentStyleDsl.StyleScope): String = ""
  def overflow_=(value: String)(using scope: ComponentStyleDsl.StyleScope): Unit =
    scope.host.setStyle("overflow", value)
  def overflow_=(value: ReadOnlyProperty[String])(using scope: ComponentStyleDsl.StyleScope, component: AbstractComponent): Unit =
    bindStyle("overflow", value)

  def zIndex(using scope: ComponentStyleDsl.StyleScope): String = ""
  def zIndex_=(value: String)(using scope: ComponentStyleDsl.StyleScope): Unit =
    scope.host.setStyle("z-index", value)
  def zIndex_=(value: ReadOnlyProperty[String])(using scope: ComponentStyleDsl.StyleScope, component: AbstractComponent): Unit =
    bindStyle("z-index", value)
}

object ComponentStyleDsl {
  final case class StyleScope(host: HostElement)
}
