package jfx.core.component

import org.scalajs.dom.{Node, console}

import scala.collection.mutable

trait ManagedElementComponent[E <: Node]
    extends ElementComponent[E], FormSubtreeRegistration {

  private val managedChildren = mutable.ArrayBuffer.empty[NodeComponent[? <: Node]]

  protected final def addChild(child: NodeComponent[? <: Node]): Unit = {
    if (managedChildren.exists(_ eq child)) {
      console.warn("Child already in list")
      return
    }

    managedChildren += child
    element.appendChild(child.element)
    child.parent = Some(this)
    child.onMount()
    registerSubtree(child)
  }

  protected final def insertChild(index: Int, child: NodeComponent[? <: Node]): Unit = {
    if (managedChildren.exists(_ eq child)) {
      console.warn("Child already in list")
      return
    }

    val boundedIndex = math.max(0, math.min(index, managedChildren.length))
    val refNode =
      if (boundedIndex >= managedChildren.length) null
      else managedChildren(boundedIndex).element

    managedChildren.insert(boundedIndex, child)
    if (refNode == null) {
      element.appendChild(child.element)
    } else {
      element.insertBefore(child.element, refNode)
    }
    child.parent = Some(this)
    child.onMount()
    registerSubtree(child)
  }

  protected final def removeChild(child: NodeComponent[? <: Node]): Unit = {
    val index = managedChildren.indexWhere(_ eq child)

    if (index >= 0) {
      managedChildren.remove(index)

      val parent = child.element.parentNode
      if (parent == element) {
        element.removeChild(child.element)
      } else if (parent != null) {
        parent.removeChild(child.element)
      }

      child.parent = None
      child.dispose()
      unregisterSubtree(child)
    }
  }

  protected final def clearChildren(): Unit =
    managedChildren.toVector.foreach(removeChild)

  override def dispose(): Unit = {
    clearChildren()
    super.dispose()
  }

  override private[jfx] def attachChild(child: NodeComponent[? <: Node]): Unit =
    addChild(child)

  override private[jfx] def detachChild(child: NodeComponent[? <: Node]): Boolean = {
    val contains = managedChildren.exists(_ eq child)
    if (contains) {
      removeChild(child)
    }
    contains
  }

  override private[jfx] def childComponentsIterator: Iterator[NodeComponent[? <: Node]] =
    managedChildren.iterator
}
