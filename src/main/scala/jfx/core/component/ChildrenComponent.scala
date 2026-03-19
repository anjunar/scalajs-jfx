package jfx.core.component

import jfx.core.component.Component
import jfx.core.state.ListProperty
import org.scalajs.dom.{HTMLElement, Node, console}

trait ChildrenComponent[E <: Node] extends Component[E] {

  val childrenProperty: ListProperty[Component[? <: Node]] =
    new ListProperty[Component[? <: Node]]()

  override def dispose(): Unit = {
    val children = childrenProperty.toList
    childrenProperty.clear()
    children.foreach(_.dispose())
    disposable.dispose()
  }

  def addChild(child: Component[? <: Node]): Unit = {
    if (childrenProperty.contains(child)) {
      console.warn("Child already in list")
      return
    }
    childrenProperty += child
  }

  def removeChild(child: Component[? <: Node]): Unit =
    childrenProperty -= child

  def insertChild(index: Int, child: Component[? <: Node]): Unit =
    if (childrenProperty.contains(child)) {
      console.warn("Child already in list")
      return
    }
    childrenProperty.insert(index, child)

  def clearChildren(): Unit =
    childrenProperty.clear()


}
