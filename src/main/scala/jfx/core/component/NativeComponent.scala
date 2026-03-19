package jfx.core.component

import jfx.core.state.ListProperty
import jfx.core.state.ListProperty.*
import org.scalajs.dom.{Element, HTMLElement, Node}

import scala.scalajs.js

trait NativeComponent[E <: Node] extends ChildrenComponent[E], FormSubtreeRegistration {

  private val childrenObserver = childrenProperty.observeChanges(onChildrenChange)
  disposable.add(childrenObserver)

  private def onChildrenChange(change: ListProperty.Change[NodeComponent[? <: Node]]): Unit =
    change match {
      case Reset(_) =>
        removeAllDomChildren()
        childrenProperty.foreach(child => {
          element.appendChild(child.element)
          child.parent = Some(this)
          child.onMount()
          registerSubtree(child)
        })

      case Add(child, _) =>
        element.appendChild(child.element)
        child.parent = Some(this)
        child.onMount()
        registerSubtree(child)

      case Insert(index, child, _) =>
        insertDomAt(index, child.element)
        child.parent = Some(this)
        child.onMount()
        registerSubtree(child)

      case InsertAll(index, children, _) =>
        insertAllDomAt(index, children.map(child => {
          child.parent = Some(this)
          child.onMount()
          registerSubtree(child)
          child.element
        }))

      case RemoveAt(_, child, _) =>
        removeDomChild(child.element)
        child.parent = None
        child.dispose()
        unregisterSubtree(child)

      case RemoveRange(_, children, _) =>
        children.foreach(child => {
          removeDomChild(child.element)
          child.parent = None
          child.dispose()
          unregisterSubtree(child)
        })

      case UpdateAt(index, oldChild, newChild, _) =>
        replaceDomAt(index, oldChild.element, newChild.element)
        oldChild.parent = None
        oldChild.dispose()
        unregisterSubtree(oldChild)
        newChild.parent = Some(this)
        newChild.onMount()
        registerSubtree(newChild)

      case Patch(from, removed, inserted, _) =>
        removed.foreach(child => {
          removeDomChild(child.element)
          child.parent = None
          child.dispose()
          unregisterSubtree(child)
        })
        insertAllDomAt(from, inserted.map(child => {
          child.parent = Some(this)
          child.onMount()
          registerSubtree(child)
          child.element
        }))

      case Clear(removed, _) =>
        removed.foreach(child => {
          child.parent = None
          child.dispose()
          removeDomChild(child.element)
          unregisterSubtree(child)
        })
    }

  private def referenceNodeAt(index: Int): Node | Null = element match {
    case element : HTMLElement =>
      if (index < 0) null
        else if (index >= element.childElementCount) null
        else element.children.item(index) 
    case _ => null
  }

  private def insertDomAt(index: Int, childElement: Node): Unit = {
    val ref = referenceNodeAt(index)
    if (ref == null) element.appendChild(childElement)
    else element.insertBefore(childElement, ref)
  }

  private def insertAllDomAt(index: Int, childElements: js.Array[Node]): Unit = {
    val ref = referenceNodeAt(index)
    if (ref == null) {
      childElements.foreach { child =>
        element.appendChild(child)
        ()
      }
    } else {
      childElements.foreach { child =>
        element.insertBefore(child, ref)
        ()
      }
    }
  }

  private def replaceDomAt(index: Int, oldChildElement: Node, newChildElement: Node): Unit = {
    val oldParent = oldChildElement.parentNode
    if (oldParent == element) {
      element.replaceChild(newChildElement, oldChildElement)
      return
    }

    insertDomAt(index, newChildElement)

    if (oldParent != null) {
      oldParent.removeChild(oldChildElement)
    }
  }

  private def removeDomChild(childElement: Node): Unit = {
    val parent = childElement.parentNode
    if (parent == element) {
      element.removeChild(childElement)
    } else if (parent != null) {
      parent.removeChild(childElement)
    }
  }

  private def removeAllDomChildren(): Unit = {
    var maybeNode: Node | Null = element.firstChild
    while (maybeNode != null) {
      val node = maybeNode.asInstanceOf[Node]
      val next = node.nextSibling
      element.removeChild(node)
      maybeNode = next
    }
  }

}
