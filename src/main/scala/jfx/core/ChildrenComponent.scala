package jfx.core

import jfx.state.ListProperty
import org.scalajs.dom.HTMLElement

trait ChildrenComponent[E <: HTMLElement] extends Component[E] {

  val childrenProperty: ListProperty[Component[? <: HTMLElement]] =
    new ListProperty[Component[? <: HTMLElement]]()

  override def dispose(): Unit = {
    val children = childrenProperty.toList
    childrenProperty.clear()
    children.foreach(_.dispose())
    disposable.dispose()
  }

  def addChild(child: Component[? <: HTMLElement]): Unit =
    childrenProperty += child

  def removeChild(child: Component[? <: HTMLElement]): Unit =
    childrenProperty -= child

  def insertChild(index: Int, child: Component[? <: HTMLElement]): Unit =
    childrenProperty.insert(index, child)

  def clearChildren(): Unit =
    childrenProperty.clear()


}
