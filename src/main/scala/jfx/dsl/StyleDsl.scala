package jfx.dsl

import jfx.core.component.ElementComponent
import jfx.core.state.{ListProperty, ReadOnlyProperty}
import jfx.control.{TableColumn, TableView}
import org.scalajs.dom.CSSStyleDeclaration

import scala.Conversion
import scala.annotation.targetName

// Mirrors the style properties exposed by scalajs-dom's CSSStyleDeclaration facade.
private[jfx] final case class StyleTarget(
  component: ElementComponent[?],
  declaration: CSSStyleDeclaration
)

final class StyleProperty private[jfx] (
  private val currentValue: () => String,
  private val assignValue: String => Unit,
  private val bindValue: ReadOnlyProperty[String] => Unit
) {

  def value: String =
    currentValue()

  def apply(): String =
    currentValue()

  def :=(value: String): Unit =
    assignValue(value)

  @targetName("bindFromProperty")
  def <--(property: ReadOnlyProperty[String]): Unit =
    bindValue(property)

  override def toString: String =
    currentValue()
}

given Conversion[StyleProperty, String] with
  def apply(value: StyleProperty): String =
    value.value

def style(init: StyleTarget ?=> Unit)(using component: ElementComponent[?]): Unit = {
  given StyleTarget = StyleTarget(component, component.css)
  init
}

def css(using component: ElementComponent[?]): CSSStyleDeclaration =
  component.css

def setProperty(name: String, value: String)(using target: StyleTarget): Unit = {
  target.component.clearStylePropertyBinding(name)
  target.declaration.setProperty(name, value)
}

def removeProperty(name: String)(using target: StyleTarget): String = {
  target.component.clearStylePropertyBinding(name)
  target.declaration.removeProperty(name)
}

def getPropertyValue(name: String)(using target: StyleTarget): String =
  target.declaration.getPropertyValue(name)

private[dsl] def styleProperty(bindingKey: String)(using target: StyleTarget): StyleProperty =
  new StyleProperty(
    currentValue = () => target.declaration.getPropertyValue(bindingKey),
    assignValue = value => {
      target.component.clearStylePropertyBinding(bindingKey)
      target.declaration.setProperty(bindingKey, value)
    },
    bindValue = property =>
      target.component.bindStyleProperty(bindingKey, property)(value => target.declaration.setProperty(bindingKey, value))
  )

def backgroundAttachment(using target: StyleTarget): StyleProperty =
  styleProperty("background-attachment")

def backgroundAttachment_=(value: String)(using target: StyleTarget): Unit =
  backgroundAttachment := value

def visibility(using target: StyleTarget): StyleProperty =
  styleProperty("visibility")

def visibility_=(value: String)(using target: StyleTarget): Unit =
  visibility := value

def textAlignLast(using target: StyleTarget): StyleProperty =
  styleProperty("text-align-last")

def textAlignLast_=(value: String)(using target: StyleTarget): Unit =
  textAlignLast := value

def borderRightStyle(using target: StyleTarget): StyleProperty =
  styleProperty("border-right-style")

def borderRightStyle_=(value: String)(using target: StyleTarget): Unit =
  borderRightStyle := value

def counterIncrement(using target: StyleTarget): StyleProperty =
  styleProperty("counter-increment")

def counterIncrement_=(value: String)(using target: StyleTarget): Unit =
  counterIncrement := value

def orphans(using target: StyleTarget): StyleProperty =
  styleProperty("orphans")

def orphans_=(value: String)(using target: StyleTarget): Unit =
  orphans := value

def borderStyle(using target: StyleTarget): StyleProperty =
  styleProperty("border-style")

def borderStyle_=(value: String)(using target: StyleTarget): Unit =
  borderStyle := value

def pointerEvents(using target: StyleTarget): StyleProperty =
  styleProperty("pointer-events")

def pointerEvents_=(value: String)(using target: StyleTarget): Unit =
  pointerEvents := value

def borderTopColor(using target: StyleTarget): StyleProperty =
  styleProperty("border-top-color")

def borderTopColor_=(value: String)(using target: StyleTarget): Unit =
  borderTopColor := value

def markerEnd(using target: StyleTarget): StyleProperty =
  styleProperty("marker-end")

def markerEnd_=(value: String)(using target: StyleTarget): Unit =
  markerEnd := value

def textIndent(using target: StyleTarget): StyleProperty =
  styleProperty("text-indent")

def textIndent_=(value: String)(using target: StyleTarget): Unit =
  textIndent := value

def listStyleImage(using target: StyleTarget): StyleProperty =
  styleProperty("list-style-image")

def listStyleImage_=(value: String)(using target: StyleTarget): Unit =
  listStyleImage := value

def cursor(using target: StyleTarget): StyleProperty =
  styleProperty("cursor")

def cursor_=(value: String)(using target: StyleTarget): Unit =
  cursor := value

def listStylePosition(using target: StyleTarget): StyleProperty =
  styleProperty("list-style-position")

def listStylePosition_=(value: String)(using target: StyleTarget): Unit =
  listStylePosition := value

def wordWrap(using target: StyleTarget): StyleProperty =
  styleProperty("word-wrap")

def wordWrap_=(value: String)(using target: StyleTarget): Unit =
  wordWrap := value

def borderTopStyle(using target: StyleTarget): StyleProperty =
  styleProperty("border-top-style")

def borderTopStyle_=(value: String)(using target: StyleTarget): Unit =
  borderTopStyle := value

def alignmentBaseline(using target: StyleTarget): StyleProperty =
  styleProperty("alignment-baseline")

def alignmentBaseline_=(value: String)(using target: StyleTarget): Unit =
  alignmentBaseline := value

def opacity(using target: StyleTarget): StyleProperty =
  styleProperty("opacity")

def opacity_=(value: String)(using target: StyleTarget): Unit =
  opacity := value

def direction(using target: StyleTarget): StyleProperty =
  styleProperty("direction")

def direction_=(value: String)(using target: StyleTarget): Unit =
  direction := value

def strokeMiterlimit(using target: StyleTarget): StyleProperty =
  styleProperty("stroke-miterlimit")

def strokeMiterlimit_=(value: String)(using target: StyleTarget): Unit =
  strokeMiterlimit := value

def maxWidth(using target: StyleTarget): StyleProperty =
  styleProperty("max-width")

def maxWidth_=(value: String)(using target: StyleTarget): Unit =
  maxWidth := value

def color(using target: StyleTarget): StyleProperty =
  styleProperty("color")

def color_=(value: String)(using target: StyleTarget): Unit =
  color := value

def clip(using target: StyleTarget): StyleProperty =
  styleProperty("clip")

def clip_=(value: String)(using target: StyleTarget): Unit =
  clip := value

def borderRightWidth(using target: StyleTarget): StyleProperty =
  styleProperty("border-right-width")

def borderRightWidth_=(value: String)(using target: StyleTarget): Unit =
  borderRightWidth := value

def verticalAlign(using target: StyleTarget): StyleProperty =
  styleProperty("vertical-align")

def verticalAlign_=(value: String)(using target: StyleTarget): Unit =
  verticalAlign := value

def overflow(using target: StyleTarget): StyleProperty =
  styleProperty("overflow")

def overflow_=(value: String)(using target: StyleTarget): Unit =
  overflow := value

def mask(using target: StyleTarget): StyleProperty =
  styleProperty("mask")

def mask_=(value: String)(using target: StyleTarget): Unit =
  mask := value

def borderLeftStyle(using target: StyleTarget): StyleProperty =
  styleProperty("border-left-style")

def borderLeftStyle_=(value: String)(using target: StyleTarget): Unit =
  borderLeftStyle := value

def emptyCells(using target: StyleTarget): StyleProperty =
  styleProperty("empty-cells")

def emptyCells_=(value: String)(using target: StyleTarget): Unit =
  emptyCells := value

def stopOpacity(using target: StyleTarget): StyleProperty =
  styleProperty("stop-opacity")

def stopOpacity_=(value: String)(using target: StyleTarget): Unit =
  stopOpacity := value

def paddingRight(using target: StyleTarget): StyleProperty =
  styleProperty("padding-right")

def paddingRight_=(value: String)(using target: StyleTarget): Unit =
  paddingRight := value

def background(using target: StyleTarget): StyleProperty =
  styleProperty("background")

def background_=(value: String)(using target: StyleTarget): Unit =
  background := value

def boxSizing(using target: StyleTarget): StyleProperty =
  styleProperty("box-sizing")

def boxSizing_=(value: String)(using target: StyleTarget): Unit =
  boxSizing := value

def textJustify(using target: StyleTarget): StyleProperty =
  styleProperty("text-justify")

def textJustify_=(value: String)(using target: StyleTarget): Unit =
  textJustify := value

def height(using target: StyleTarget): StyleProperty =
  styleProperty("height")

def height_=(value: String)(using target: StyleTarget): Unit =
  height := value

def paddingTop(using target: StyleTarget): StyleProperty =
  styleProperty("padding-top")

def paddingTop_=(value: String)(using target: StyleTarget): Unit =
  paddingTop := value

def right(using target: StyleTarget): StyleProperty =
  styleProperty("right")

def right_=(value: String)(using target: StyleTarget): Unit =
  right := value

def baselineShift(using target: StyleTarget): StyleProperty =
  styleProperty("baseline-shift")

def baselineShift_=(value: String)(using target: StyleTarget): Unit =
  baselineShift := value

def borderLeft(using target: StyleTarget): StyleProperty =
  styleProperty("border-left")

def borderLeft_=(value: String)(using target: StyleTarget): Unit =
  borderLeft := value

def widows(using target: StyleTarget): StyleProperty =
  styleProperty("widows")

def widows_=(value: String)(using target: StyleTarget): Unit =
  widows := value

def lineHeight(using target: StyleTarget): StyleProperty =
  styleProperty("line-height")

def lineHeight_=(value: String)(using target: StyleTarget): Unit =
  lineHeight := value

def left(using target: StyleTarget): StyleProperty =
  styleProperty("left")

def left_=(value: String)(using target: StyleTarget): Unit =
  left := value

def textUnderlinePosition(using target: StyleTarget): StyleProperty =
  styleProperty("text-underline-position")

def textUnderlinePosition_=(value: String)(using target: StyleTarget): Unit =
  textUnderlinePosition := value

def glyphOrientationHorizontal(using target: StyleTarget): StyleProperty =
  styleProperty("glyph-orientation-horizontal")

def glyphOrientationHorizontal_=(value: String)(using target: StyleTarget): Unit =
  glyphOrientationHorizontal := value

def display(using target: StyleTarget): StyleProperty =
  styleProperty("display")

def display_=(value: String)(using target: StyleTarget): Unit =
  display := value

def textAnchor(using target: StyleTarget): StyleProperty =
  styleProperty("text-anchor")

def textAnchor_=(value: String)(using target: StyleTarget): Unit =
  textAnchor := value

def cssFloat(using target: StyleTarget): StyleProperty =
  styleProperty("float")

def cssFloat_=(value: String)(using target: StyleTarget): Unit =
  cssFloat := value

def float(using target: StyleTarget): StyleProperty =
  cssFloat

def float_=(value: String)(using target: StyleTarget): Unit =
  cssFloat := value

def strokeDasharray(using target: StyleTarget): StyleProperty =
  styleProperty("stroke-dasharray")

def strokeDasharray_=(value: String)(using target: StyleTarget): Unit =
  strokeDasharray := value

def rubyAlign(using target: StyleTarget): StyleProperty =
  styleProperty("ruby-align")

def rubyAlign_=(value: String)(using target: StyleTarget): Unit =
  rubyAlign := value

def fontSizeAdjust(using target: StyleTarget): StyleProperty =
  styleProperty("font-size-adjust")

def fontSizeAdjust_=(value: String)(using target: StyleTarget): Unit =
  fontSizeAdjust := value

def borderLeftColor(using target: StyleTarget): StyleProperty =
  styleProperty("border-left-color")

def borderLeftColor_=(value: String)(using target: StyleTarget): Unit =
  borderLeftColor := value

def backgroundImage(using target: StyleTarget): StyleProperty =
  styleProperty("background-image")

def backgroundImage_=(value: String)(using target: StyleTarget): Unit =
  backgroundImage := value

def listStyleType(using target: StyleTarget): StyleProperty =
  styleProperty("list-style-type")

def listStyleType_=(value: String)(using target: StyleTarget): Unit =
  listStyleType := value

def strokeWidth(using target: StyleTarget): StyleProperty =
  styleProperty("stroke-width")

def strokeWidth_=(value: String)(using target: StyleTarget): Unit =
  strokeWidth := value

def textOverflow(using target: StyleTarget): StyleProperty =
  styleProperty("text-overflow")

def textOverflow_=(value: String)(using target: StyleTarget): Unit =
  textOverflow := value

def fillRule(using target: StyleTarget): StyleProperty =
  styleProperty("fill-rule")

def fillRule_=(value: String)(using target: StyleTarget): Unit =
  fillRule := value

def borderBottomColor(using target: StyleTarget): StyleProperty =
  styleProperty("border-bottom-color")

def borderBottomColor_=(value: String)(using target: StyleTarget): Unit =
  borderBottomColor := value

def zIndex(using target: StyleTarget): StyleProperty =
  styleProperty("z-index")

def zIndex_=(value: String)(using target: StyleTarget): Unit =
  zIndex := value

def position(using target: StyleTarget): StyleProperty =
  styleProperty("position")

def position_=(value: String)(using target: StyleTarget): Unit =
  position := value

def listStyle(using target: StyleTarget): StyleProperty =
  styleProperty("list-style")

def listStyle_=(value: String)(using target: StyleTarget): Unit =
  listStyle := value

def dominantBaseline(using target: StyleTarget): StyleProperty =
  styleProperty("dominant-baseline")

def dominantBaseline_=(value: String)(using target: StyleTarget): Unit =
  dominantBaseline := value

def overflowY(using target: StyleTarget): StyleProperty =
  styleProperty("overflow-y")

def overflowY_=(value: String)(using target: StyleTarget): Unit =
  overflowY := value

def fill(using target: StyleTarget): StyleProperty =
  styleProperty("fill")

def fill_=(value: String)(using target: StyleTarget): Unit =
  fill := value

def captionSide(using target: StyleTarget): StyleProperty =
  styleProperty("caption-side")

def captionSide_=(value: String)(using target: StyleTarget): Unit =
  captionSide := value

def borderCollapse(using target: StyleTarget): StyleProperty =
  styleProperty("border-collapse")

def borderCollapse_=(value: String)(using target: StyleTarget): Unit =
  borderCollapse := value

def boxShadow(using target: StyleTarget): StyleProperty =
  styleProperty("box-shadow")

def boxShadow_=(value: String)(using target: StyleTarget): Unit =
  boxShadow := value

def quotes(using target: StyleTarget): StyleProperty =
  styleProperty("quotes")

def quotes_=(value: String)(using target: StyleTarget): Unit =
  quotes := value

def tableLayout(using target: StyleTarget): StyleProperty =
  styleProperty("table-layout")

def tableLayout_=(value: String)(using target: StyleTarget): Unit =
  tableLayout := value

def unicodeBidi(using target: StyleTarget): StyleProperty =
  styleProperty("unicode-bidi")

def unicodeBidi_=(value: String)(using target: StyleTarget): Unit =
  unicodeBidi := value

def borderBottomWidth(using target: StyleTarget): StyleProperty =
  styleProperty("border-bottom-width")

def borderBottomWidth_=(value: String)(using target: StyleTarget): Unit =
  borderBottomWidth := value

def backgroundSize(using target: StyleTarget): StyleProperty =
  styleProperty("background-size")

def backgroundSize_=(value: String)(using target: StyleTarget): Unit =
  backgroundSize := value

def textDecoration(using target: StyleTarget): StyleProperty =
  styleProperty("text-decoration")

def textDecoration_=(value: String)(using target: StyleTarget): Unit =
  textDecoration := value

def strokeDashoffset(using target: StyleTarget): StyleProperty =
  styleProperty("stroke-dashoffset")

def strokeDashoffset_=(value: String)(using target: StyleTarget): Unit =
  strokeDashoffset := value

def fontSize(using target: StyleTarget): StyleProperty =
  styleProperty("font-size")

def fontSize_=(value: String)(using target: StyleTarget): Unit =
  fontSize := value

def border(using target: StyleTarget): StyleProperty =
  styleProperty("border")

def border_=(value: String)(using target: StyleTarget): Unit =
  border := value

def pageBreakBefore(using target: StyleTarget): StyleProperty =
  styleProperty("page-break-before")

def pageBreakBefore_=(value: String)(using target: StyleTarget): Unit =
  pageBreakBefore := value

def borderTopRightRadius(using target: StyleTarget): StyleProperty =
  styleProperty("border-top-right-radius")

def borderTopRightRadius_=(value: String)(using target: StyleTarget): Unit =
  borderTopRightRadius := value

def borderBottomLeftRadius(using target: StyleTarget): StyleProperty =
  styleProperty("border-bottom-left-radius")

def borderBottomLeftRadius_=(value: String)(using target: StyleTarget): Unit =
  borderBottomLeftRadius := value

def textTransform(using target: StyleTarget): StyleProperty =
  styleProperty("text-transform")

def textTransform_=(value: String)(using target: StyleTarget): Unit =
  textTransform := value

def rubyPosition(using target: StyleTarget): StyleProperty =
  styleProperty("ruby-position")

def rubyPosition_=(value: String)(using target: StyleTarget): Unit =
  rubyPosition := value

def strokeLinejoin(using target: StyleTarget): StyleProperty =
  styleProperty("stroke-linejoin")

def strokeLinejoin_=(value: String)(using target: StyleTarget): Unit =
  strokeLinejoin := value

def clipPath(using target: StyleTarget): StyleProperty =
  styleProperty("clip-path")

def clipPath_=(value: String)(using target: StyleTarget): Unit =
  clipPath := value

def borderRightColor(using target: StyleTarget): StyleProperty =
  styleProperty("border-right-color")

def borderRightColor_=(value: String)(using target: StyleTarget): Unit =
  borderRightColor := value

def fontFamily(using target: StyleTarget): StyleProperty =
  styleProperty("font-family")

def fontFamily_=(value: String)(using target: StyleTarget): Unit =
  fontFamily := value

def clear(using target: StyleTarget): StyleProperty =
  styleProperty("clear")

def clear_=(value: String)(using target: StyleTarget): Unit =
  clear := value

def content(using target: StyleTarget): StyleProperty =
  styleProperty("content")

def content_=(value: String)(using target: StyleTarget): Unit =
  content := value

def backgroundClip(using target: StyleTarget): StyleProperty =
  styleProperty("background-clip")

def backgroundClip_=(value: String)(using target: StyleTarget): Unit =
  backgroundClip := value

def marginBottom(using target: StyleTarget): StyleProperty =
  styleProperty("margin-bottom")

def marginBottom_=(value: String)(using target: StyleTarget): Unit =
  marginBottom := value

def counterReset(using target: StyleTarget): StyleProperty =
  styleProperty("counter-reset")

def counterReset_=(value: String)(using target: StyleTarget): Unit =
  counterReset := value

def outlineWidth(using target: StyleTarget): StyleProperty =
  styleProperty("outline-width")

def outlineWidth_=(value: String)(using target: StyleTarget): Unit =
  outlineWidth := value

def marginRight(using target: StyleTarget): StyleProperty =
  styleProperty("margin-right")

def marginRight_=(value: String)(using target: StyleTarget): Unit =
  marginRight := value

def paddingLeft(using target: StyleTarget): StyleProperty =
  styleProperty("padding-left")

def paddingLeft_=(value: String)(using target: StyleTarget): Unit =
  paddingLeft := value

def borderBottom(using target: StyleTarget): StyleProperty =
  styleProperty("border-bottom")

def borderBottom_=(value: String)(using target: StyleTarget): Unit =
  borderBottom := value

def wordBreak(using target: StyleTarget): StyleProperty =
  styleProperty("word-break")

def wordBreak_=(value: String)(using target: StyleTarget): Unit =
  wordBreak := value

def marginTop(using target: StyleTarget): StyleProperty =
  styleProperty("margin-top")

def marginTop_=(value: String)(using target: StyleTarget): Unit =
  marginTop := value

def top(using target: StyleTarget): StyleProperty =
  styleProperty("top")

def top_=(value: String)(using target: StyleTarget): Unit =
  top := value

def fontWeight(using target: StyleTarget): StyleProperty =
  styleProperty("font-weight")

def fontWeight_=(value: String)(using target: StyleTarget): Unit =
  fontWeight := value

def borderRight(using target: StyleTarget): StyleProperty =
  styleProperty("border-right")

def borderRight_=(value: String)(using target: StyleTarget): Unit =
  borderRight := value

def width(using target: StyleTarget): StyleProperty =
  styleProperty("width")

def width_=(value: String)(using target: StyleTarget): Unit =
  width := value

def kerning(using target: StyleTarget): StyleProperty =
  styleProperty("kerning")

def kerning_=(value: String)(using target: StyleTarget): Unit =
  kerning := value

def pageBreakAfter(using target: StyleTarget): StyleProperty =
  styleProperty("page-break-after")

def pageBreakAfter_=(value: String)(using target: StyleTarget): Unit =
  pageBreakAfter := value

def borderBottomStyle(using target: StyleTarget): StyleProperty =
  styleProperty("border-bottom-style")

def borderBottomStyle_=(value: String)(using target: StyleTarget): Unit =
  borderBottomStyle := value

def fontStretch(using target: StyleTarget): StyleProperty =
  styleProperty("font-stretch")

def fontStretch_=(value: String)(using target: StyleTarget): Unit =
  fontStretch := value

def padding(using target: StyleTarget): StyleProperty =
  styleProperty("padding")

def padding_=(value: String)(using target: StyleTarget): Unit =
  padding := value

def strokeOpacity(using target: StyleTarget): StyleProperty =
  styleProperty("stroke-opacity")

def strokeOpacity_=(value: String)(using target: StyleTarget): Unit =
  strokeOpacity := value

def markerStart(using target: StyleTarget): StyleProperty =
  styleProperty("marker-start")

def markerStart_=(value: String)(using target: StyleTarget): Unit =
  markerStart := value

def bottom(using target: StyleTarget): StyleProperty =
  styleProperty("bottom")

def bottom_=(value: String)(using target: StyleTarget): Unit =
  bottom := value

def borderLeftWidth(using target: StyleTarget): StyleProperty =
  styleProperty("border-left-width")

def borderLeftWidth_=(value: String)(using target: StyleTarget): Unit =
  borderLeftWidth := value

def clipRule(using target: StyleTarget): StyleProperty =
  styleProperty("clip-rule")

def clipRule_=(value: String)(using target: StyleTarget): Unit =
  clipRule := value

def backgroundPosition(using target: StyleTarget): StyleProperty =
  styleProperty("background-position")

def backgroundPosition_=(value: String)(using target: StyleTarget): Unit =
  backgroundPosition := value

def backgroundColor(using target: StyleTarget): StyleProperty =
  styleProperty("background-color")

def backgroundColor_=(value: String)(using target: StyleTarget): Unit =
  backgroundColor := value

def pageBreakInside(using target: StyleTarget): StyleProperty =
  styleProperty("page-break-inside")

def pageBreakInside_=(value: String)(using target: StyleTarget): Unit =
  pageBreakInside := value

def backgroundOrigin(using target: StyleTarget): StyleProperty =
  styleProperty("background-origin")

def backgroundOrigin_=(value: String)(using target: StyleTarget): Unit =
  backgroundOrigin := value

def strokeLinecap(using target: StyleTarget): StyleProperty =
  styleProperty("stroke-linecap")

def strokeLinecap_=(value: String)(using target: StyleTarget): Unit =
  strokeLinecap := value

def borderTopWidth(using target: StyleTarget): StyleProperty =
  styleProperty("border-top-width")

def borderTopWidth_=(value: String)(using target: StyleTarget): Unit =
  borderTopWidth := value

def outlineStyle(using target: StyleTarget): StyleProperty =
  styleProperty("outline-style")

def outlineStyle_=(value: String)(using target: StyleTarget): Unit =
  outlineStyle := value

def borderTop(using target: StyleTarget): StyleProperty =
  styleProperty("border-top")

def borderTop_=(value: String)(using target: StyleTarget): Unit =
  borderTop := value

def outlineColor(using target: StyleTarget): StyleProperty =
  styleProperty("outline-color")

def outlineColor_=(value: String)(using target: StyleTarget): Unit =
  outlineColor := value

def paddingBottom(using target: StyleTarget): StyleProperty =
  styleProperty("padding-bottom")

def paddingBottom_=(value: String)(using target: StyleTarget): Unit =
  paddingBottom := value

def marginLeft(using target: StyleTarget): StyleProperty =
  styleProperty("margin-left")

def marginLeft_=(value: String)(using target: StyleTarget): Unit =
  marginLeft := value

def font(using target: StyleTarget): StyleProperty =
  styleProperty("font")

def font_=(value: String)(using target: StyleTarget): Unit =
  font := value

def outline(using target: StyleTarget): StyleProperty =
  styleProperty("outline")

def outline_=(value: String)(using target: StyleTarget): Unit =
  outline := value

def wordSpacing(using target: StyleTarget): StyleProperty =
  styleProperty("word-spacing")

def wordSpacing_=(value: String)(using target: StyleTarget): Unit =
  wordSpacing := value

def maxHeight(using target: StyleTarget): StyleProperty =
  styleProperty("max-height")

def maxHeight_=(value: String)(using target: StyleTarget): Unit =
  maxHeight := value

def fillOpacity(using target: StyleTarget): StyleProperty =
  styleProperty("fill-opacity")

def fillOpacity_=(value: String)(using target: StyleTarget): Unit =
  fillOpacity := value

def letterSpacing(using target: StyleTarget): StyleProperty =
  styleProperty("letter-spacing")

def letterSpacing_=(value: String)(using target: StyleTarget): Unit =
  letterSpacing := value

def borderSpacing(using target: StyleTarget): StyleProperty =
  styleProperty("border-spacing")

def borderSpacing_=(value: String)(using target: StyleTarget): Unit =
  borderSpacing := value

def backgroundRepeat(using target: StyleTarget): StyleProperty =
  styleProperty("background-repeat")

def backgroundRepeat_=(value: String)(using target: StyleTarget): Unit =
  backgroundRepeat := value

def borderRadius(using target: StyleTarget): StyleProperty =
  styleProperty("border-radius")

def borderRadius_=(value: String)(using target: StyleTarget): Unit =
  borderRadius := value

def borderWidth(using target: StyleTarget): StyleProperty =
  styleProperty("border-width")

def borderWidth_=(value: String)(using target: StyleTarget): Unit =
  borderWidth := value

def borderBottomRightRadius(using target: StyleTarget): StyleProperty =
  styleProperty("border-bottom-right-radius")

def borderBottomRightRadius_=(value: String)(using target: StyleTarget): Unit =
  borderBottomRightRadius := value

def whiteSpace(using target: StyleTarget): StyleProperty =
  styleProperty("white-space")

def whiteSpace_=(value: String)(using target: StyleTarget): Unit =
  whiteSpace := value

def fontStyle(using target: StyleTarget): StyleProperty =
  styleProperty("font-style")

def fontStyle_=(value: String)(using target: StyleTarget): Unit =
  fontStyle := value

def minWidth(using target: StyleTarget): StyleProperty =
  styleProperty("min-width")

def minWidth_=(value: String)(using target: StyleTarget): Unit =
  minWidth := value

def stopColor(using target: StyleTarget): StyleProperty =
  styleProperty("stop-color")

def stopColor_=(value: String)(using target: StyleTarget): Unit =
  stopColor := value

def borderTopLeftRadius(using target: StyleTarget): StyleProperty =
  styleProperty("border-top-left-radius")

def borderTopLeftRadius_=(value: String)(using target: StyleTarget): Unit =
  borderTopLeftRadius := value

def borderColor(using target: StyleTarget): StyleProperty =
  styleProperty("border-color")

def borderColor_=(value: String)(using target: StyleTarget): Unit =
  borderColor := value

def marker(using target: StyleTarget): StyleProperty =
  styleProperty("marker")

def marker_=(value: String)(using target: StyleTarget): Unit =
  marker := value

def glyphOrientationVertical(using target: StyleTarget): StyleProperty =
  styleProperty("glyph-orientation-vertical")

def glyphOrientationVertical_=(value: String)(using target: StyleTarget): Unit =
  glyphOrientationVertical := value

def markerMid(using target: StyleTarget): StyleProperty =
  styleProperty("marker-mid")

def markerMid_=(value: String)(using target: StyleTarget): Unit =
  markerMid := value

def fontVariant(using target: StyleTarget): StyleProperty =
  styleProperty("font-variant")

def fontVariant_=(value: String)(using target: StyleTarget): Unit =
  fontVariant := value

def minHeight(using target: StyleTarget): StyleProperty =
  styleProperty("min-height")

def minHeight_=(value: String)(using target: StyleTarget): Unit =
  minHeight := value

def stroke(using target: StyleTarget): StyleProperty =
  styleProperty("stroke")

def stroke_=(value: String)(using target: StyleTarget): Unit =
  stroke := value

def rubyOverhang(using target: StyleTarget): StyleProperty =
  styleProperty("ruby-overhang")

def rubyOverhang_=(value: String)(using target: StyleTarget): Unit =
  rubyOverhang := value

def overflowX(using target: StyleTarget): StyleProperty =
  styleProperty("overflow-x")

def overflowX_=(value: String)(using target: StyleTarget): Unit =
  overflowX := value

def textAlign(using target: StyleTarget): StyleProperty =
  styleProperty("text-align")

def textAlign_=(value: String)(using target: StyleTarget): Unit =
  textAlign := value

def margin(using target: StyleTarget): StyleProperty =
  styleProperty("margin")

def margin_=(value: String)(using target: StyleTarget): Unit =
  margin := value

def animationFillMode(using target: StyleTarget): StyleProperty =
  styleProperty("animation-fill-mode")

def animationFillMode_=(value: String)(using target: StyleTarget): Unit =
  animationFillMode := value

def floodColor(using target: StyleTarget): StyleProperty =
  styleProperty("flood-color")

def floodColor_=(value: String)(using target: StyleTarget): Unit =
  floodColor := value

def animationIterationCount(using target: StyleTarget): StyleProperty =
  styleProperty("animation-iteration-count")

def animationIterationCount_=(value: String)(using target: StyleTarget): Unit =
  animationIterationCount := value

def textShadow(using target: StyleTarget): StyleProperty =
  styleProperty("text-shadow")

def textShadow_=(value: String)(using target: StyleTarget): Unit =
  textShadow := value

def backfaceVisibility(using target: StyleTarget): StyleProperty =
  styleProperty("backface-visibility")

def backfaceVisibility_=(value: String)(using target: StyleTarget): Unit =
  backfaceVisibility := value

def animationDelay(using target: StyleTarget): StyleProperty =
  styleProperty("animation-delay")

def animationDelay_=(value: String)(using target: StyleTarget): Unit =
  animationDelay := value

def animationTimingFunction(using target: StyleTarget): StyleProperty =
  styleProperty("animation-timing-function")

def animationTimingFunction_=(value: String)(using target: StyleTarget): Unit =
  animationTimingFunction := value

def columnWidth(using target: StyleTarget): StyleProperty =
  styleProperty("column-width")

def columnWidth_=(value: String)(using target: StyleTarget): Unit =
  columnWidth := value

def columnRuleColor(using target: StyleTarget): StyleProperty =
  styleProperty("column-rule-color")

def columnRuleColor_=(value: String)(using target: StyleTarget): Unit =
  columnRuleColor := value

def columnRuleWidth(using target: StyleTarget): StyleProperty =
  styleProperty("column-rule-width")

def columnRuleWidth_=(value: String)(using target: StyleTarget): Unit =
  columnRuleWidth := value

def transitionDelay(using target: StyleTarget): StyleProperty =
  styleProperty("transition-delay")

def transitionDelay_=(value: String)(using target: StyleTarget): Unit =
  transitionDelay := value

def transition(using target: StyleTarget): StyleProperty =
  styleProperty("transition")

def transition_=(value: String)(using target: StyleTarget): Unit =
  transition := value

def enableBackground(using target: StyleTarget): StyleProperty =
  styleProperty("enable-background")

def enableBackground_=(value: String)(using target: StyleTarget): Unit =
  enableBackground := value

def columnRuleStyle(using target: StyleTarget): StyleProperty =
  styleProperty("column-rule-style")

def columnRuleStyle_=(value: String)(using target: StyleTarget): Unit =
  columnRuleStyle := value

def animation(using target: StyleTarget): StyleProperty =
  styleProperty("animation")

def animation_=(value: String)(using target: StyleTarget): Unit =
  animation := value

def transform(using target: StyleTarget): StyleProperty =
  styleProperty("transform")

def transform_=(value: String)(using target: StyleTarget): Unit =
  transform := value

def colorInterpolationFilters(using target: StyleTarget): StyleProperty =
  styleProperty("color-interpolation-filters")

def colorInterpolationFilters_=(value: String)(using target: StyleTarget): Unit =
  colorInterpolationFilters := value

def transitionTimingFunction(using target: StyleTarget): StyleProperty =
  styleProperty("transition-timing-function")

def transitionTimingFunction_=(value: String)(using target: StyleTarget): Unit =
  transitionTimingFunction := value

def animationPlayState(using target: StyleTarget): StyleProperty =
  styleProperty("animation-play-state")

def animationPlayState_=(value: String)(using target: StyleTarget): Unit =
  animationPlayState := value

def transformOrigin(using target: StyleTarget): StyleProperty =
  styleProperty("transform-origin")

def transformOrigin_=(value: String)(using target: StyleTarget): Unit =
  transformOrigin := value

def columnGap(using target: StyleTarget): StyleProperty =
  styleProperty("column-gap")

def columnGap_=(value: String)(using target: StyleTarget): Unit =
  columnGap := value

def transitionProperty(using target: StyleTarget): StyleProperty =
  styleProperty("transition-property")

def transitionProperty_=(value: String)(using target: StyleTarget): Unit =
  transitionProperty := value

def fontFeatureSettings(using target: StyleTarget): StyleProperty =
  styleProperty("font-feature-settings")

def fontFeatureSettings_=(value: String)(using target: StyleTarget): Unit =
  fontFeatureSettings := value

def breakBefore(using target: StyleTarget): StyleProperty =
  styleProperty("break-before")

def breakBefore_=(value: String)(using target: StyleTarget): Unit =
  breakBefore := value

def perspective(using target: StyleTarget): StyleProperty =
  styleProperty("perspective")

def perspective_=(value: String)(using target: StyleTarget): Unit =
  perspective := value

def animationDirection(using target: StyleTarget): StyleProperty =
  styleProperty("animation-direction")

def animationDirection_=(value: String)(using target: StyleTarget): Unit =
  animationDirection := value

def animationDuration(using target: StyleTarget): StyleProperty =
  styleProperty("animation-duration")

def animationDuration_=(value: String)(using target: StyleTarget): Unit =
  animationDuration := value

def animationName(using target: StyleTarget): StyleProperty =
  styleProperty("animation-name")

def animationName_=(value: String)(using target: StyleTarget): Unit =
  animationName := value

def columnRule(using target: StyleTarget): StyleProperty =
  styleProperty("column-rule")

def columnRule_=(value: String)(using target: StyleTarget): Unit =
  columnRule := value

def columnFill(using target: StyleTarget): StyleProperty =
  styleProperty("column-fill")

def columnFill_=(value: String)(using target: StyleTarget): Unit =
  columnFill := value

def perspectiveOrigin(using target: StyleTarget): StyleProperty =
  styleProperty("perspective-origin")

def perspectiveOrigin_=(value: String)(using target: StyleTarget): Unit =
  perspectiveOrigin := value

def lightingColor(using target: StyleTarget): StyleProperty =
  styleProperty("lighting-color")

def lightingColor_=(value: String)(using target: StyleTarget): Unit =
  lightingColor := value

def columns(using target: StyleTarget): StyleProperty =
  styleProperty("columns")

def columns_=(value: String)(using target: StyleTarget): Unit =
  columns := value

def floodOpacity(using target: StyleTarget): StyleProperty =
  styleProperty("flood-opacity")

def floodOpacity_=(value: String)(using target: StyleTarget): Unit =
  floodOpacity := value

def columnSpan(using target: StyleTarget): StyleProperty =
  styleProperty("column-span")

def columnSpan_=(value: String)(using target: StyleTarget): Unit =
  columnSpan := value

def breakInside(using target: StyleTarget): StyleProperty =
  styleProperty("break-inside")

def breakInside_=(value: String)(using target: StyleTarget): Unit =
  breakInside := value

def transitionDuration(using target: StyleTarget): StyleProperty =
  styleProperty("transition-duration")

def transitionDuration_=(value: String)(using target: StyleTarget): Unit =
  transitionDuration := value

def breakAfter(using target: StyleTarget): StyleProperty =
  styleProperty("break-after")

def breakAfter_=(value: String)(using target: StyleTarget): Unit =
  breakAfter := value

def columnCount(using target: StyleTarget): StyleProperty =
  styleProperty("column-count")

def columnCount_=(value: String)(using target: StyleTarget): Unit =
  columnCount := value

def transformStyle(using target: StyleTarget): StyleProperty =
  styleProperty("transform-style")

def transformStyle_=(value: String)(using target: StyleTarget): Unit =
  transformStyle := value

def flex(using target: StyleTarget): StyleProperty =
  styleProperty("flex")

def flex_=(value: String)(using target: StyleTarget): Unit =
  flex := value

def flexBasis(using target: StyleTarget): StyleProperty =
  styleProperty("flex-basis")

def flexBasis_=(value: String)(using target: StyleTarget): Unit =
  flexBasis := value

def flexDirection(using target: StyleTarget): StyleProperty =
  styleProperty("flex-direction")

def flexDirection_=(value: String)(using target: StyleTarget): Unit =
  flexDirection := value

def flexFlow(using target: StyleTarget): StyleProperty =
  styleProperty("flex-flow")

def flexFlow_=(value: String)(using target: StyleTarget): Unit =
  flexFlow := value

def flexGrow(using target: StyleTarget): StyleProperty =
  styleProperty("flex-grow")

def flexGrow_=(value: String)(using target: StyleTarget): Unit =
  flexGrow := value

def flexShrink(using target: StyleTarget): StyleProperty =
  styleProperty("flex-shrink")

def flexShrink_=(value: String)(using target: StyleTarget): Unit =
  flexShrink := value

def flexWrap(using target: StyleTarget): StyleProperty =
  styleProperty("flex-wrap")

def flexWrap_=(value: String)(using target: StyleTarget): Unit =
  flexWrap := value

def gap(using target: StyleTarget): StyleProperty =
  styleProperty("gap")

def gap_=(value: String)(using target: StyleTarget): Unit =
  gap := value

def rowGap(using target: StyleTarget): StyleProperty =
  styleProperty("row-gap")

def rowGap_=(value: String)(using target: StyleTarget): Unit =
  rowGap := value

def justifyContent(using target: StyleTarget): StyleProperty =
  styleProperty("justify-content")

def justifyContent_=(value: String)(using target: StyleTarget): Unit =
  justifyContent := value

def justifyItems(using target: StyleTarget): StyleProperty =
  styleProperty("justify-items")

def justifyItems_=(value: String)(using target: StyleTarget): Unit =
  justifyItems := value

def justifySelf(using target: StyleTarget): StyleProperty =
  styleProperty("justify-self")

def justifySelf_=(value: String)(using target: StyleTarget): Unit =
  justifySelf := value

def alignContent(using target: StyleTarget): StyleProperty =
  styleProperty("align-content")

def alignContent_=(value: String)(using target: StyleTarget): Unit =
  alignContent := value

def alignItems(using target: StyleTarget): StyleProperty =
  styleProperty("align-items")

def alignItems_=(value: String)(using target: StyleTarget): Unit =
  alignItems := value

def alignSelf(using target: StyleTarget): StyleProperty =
  styleProperty("align-self")

def alignSelf_=(value: String)(using target: StyleTarget): Unit =
  alignSelf := value

def placeContent(using target: StyleTarget): StyleProperty =
  styleProperty("place-content")

def placeContent_=(value: String)(using target: StyleTarget): Unit =
  placeContent := value

def placeItems(using target: StyleTarget): StyleProperty =
  styleProperty("place-items")

def placeItems_=(value: String)(using target: StyleTarget): Unit =
  placeItems := value

def placeSelf(using target: StyleTarget): StyleProperty =
  styleProperty("place-self")

def placeSelf_=(value: String)(using target: StyleTarget): Unit =
  placeSelf := value

def order(using target: StyleTarget): StyleProperty =
  styleProperty("order")

def order_=(value: String)(using target: StyleTarget): Unit =
  order := value

def columns[S](using tableView: TableView[S]): ListProperty[TableColumn[S, ?]] =
  tableView.columnsProperty

def columns_=[S](value: IterableOnce[TableColumn[S, ?]])(using tableView: TableView[S]): Unit = {
  tableView.columnsProperty.clear()
  value.iterator.foreach(column => tableView.columnsProperty += column)
}

def minWidth(using tableColumn: TableColumn[?, ?]): Double =
  tableColumn.getMinWidth

def minWidth_=(value: Double)(using tableColumn: TableColumn[?, ?]): Unit =
  tableColumn.setMinWidth(value)

def maxWidth_=(value: Double)(using tableColumn: TableColumn[?, ?]): Unit =
  tableColumn.setMaxWidth(value)
