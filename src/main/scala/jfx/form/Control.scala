package jfx.form

import jfx.core.component.Component
import jfx.core.state.Property
import org.scalajs.dom.HTMLElement

trait Control[V, E <: HTMLElement] extends Component[E] {
  
  val name : String
  
  val valueProperty : Property[V]

}
