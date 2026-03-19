package jfx.core

import jfx.form.{Control, Formular}
import jfx.state.ListProperty
import jfx.state.ListProperty.*
import org.scalajs.dom.{HTMLElement, Node}

trait NativeComponent[E <: HTMLElement] extends ChildrenComponent[E] {

  private val childrenObserver = childrenProperty.observeChanges(onChildrenChange)

  disposable.add(childrenObserver)
  
  private def onChildrenChange(change: ListProperty.Change[Component[? <: HTMLElement]]): Unit =
    change match {
      case Reset(_) =>
        removeAllDomChildren()
        childrenProperty.foreach(child => {
          element.appendChild(child.element)
          child.parent = Some(this)
          
          child match {
            case control : Control => findParentForm().addControl(control)  
          }
          
        })

      case Add(child, _) =>
        element.appendChild(child.element)
        child.parent = Some(this)

      case Insert(index, child, _) =>
        insertDomAt(index, child.element)
        child.parent = Some(this)

      case InsertAll(index, children, _) =>
        insertAllDomAt(index, children.map(child => {
          child.parent = Some(this)
          child.element
        }))

      case RemoveAt(_, child, _) =>
        removeDomChild(child.element)
        child.parent = None

      case RemoveRange(_, children, _) =>
        children.foreach(child => {
          removeDomChild(child.element)
          child.parent = None
        })

      case UpdateAt(index, oldChild, newChild, _) =>
        replaceDomAt(index, oldChild.element, newChild.element)
        oldChild.parent = None
        newChild.parent = Some(this)

      case Patch(from, removed, inserted, _) =>
        removed.foreach(child => {
          removeDomChild(child.element)
          child.parent = None
        })
        insertAllDomAt(from, inserted.map(child => {
          child.parent = Some(this)
          child.element
        }))

      case Clear(removed, _) =>
        removed.foreach(child => {
          child.parent = None
          removeDomChild(child.element)
        })
    }

  private def referenceNodeAt(index: Int): Node | Null = {
    if (index < 0) null
    else if (index >= element.childElementCount) null
    else element.children.item(index)
  }

  private def insertDomAt(index: Int, childElement: HTMLElement): Unit = {
    val ref = referenceNodeAt(index)
    if (ref == null) element.appendChild(childElement)
    else element.insertBefore(childElement, ref)
  }

  private def insertAllDomAt(index: Int, childElements: scala.scalajs.js.Array[HTMLElement]): Unit = {
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

  private def replaceDomAt(index: Int, oldChildElement: HTMLElement, newChildElement: HTMLElement): Unit = {
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

  private def removeDomChild(childElement: HTMLElement): Unit = {
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
