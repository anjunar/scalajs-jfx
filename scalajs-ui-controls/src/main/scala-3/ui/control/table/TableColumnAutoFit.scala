package ui.control.table

import ui.core.component.AbstractComponent
import ui.core.render.DomHostElement
import org.scalajs.dom

/** Measure intrinsic CSS sizing in place: no cloned editors, detached DOM or renderer calls. The
  * temporary constraints are restored synchronously, even if layout measurement throws.
  */
private[table] object TableColumnAutoFit {
  val sampleLimit = 100

  def measure(component: AbstractComponent): Option[Double] = component.host match {
    case host: DomHostElement =>
      val element = host.node.asInstanceOf[dom.HTMLElement]
      if (element.getClientRects().length == 0) None
      else {
        val constraints =
          Vector("width" -> "max-content", "min-width" -> "0px", "max-width" -> "none")
        val previous = constraints.map { case (name, _) => name -> host.style(name) }
        try {
          constraints.foreach { case (name, value) => component.setStyle(name, value) }
          val css                          = dom.window.getComputedStyle(element)
          def pixels(name: String): Double =
            css.getPropertyValue(name).stripSuffix("px").trim.toDoubleOption.getOrElse(0.0)
          val extra =
            if (css.boxSizing == "border-box") 0.0
            else
              pixels("padding-left") + pixels("padding-right") +
                pixels("border-left-width") + pixels("border-right-width")
          val width = pixels("width") + extra
          Option.when(width.isFinite && width > 0)(math.ceil(width))
        } finally
          previous.foreach {
            case (name, Some(value)) => component.setStyle(name, value)
            case (name, None)        => component.removeStyle(name)
          }
      }
    case _ => None
  }
}
