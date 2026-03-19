package jfx.statement

import jfx.core.component.{ElementComponent, FormSubtreeRegistration, NodeComponent}
import jfx.core.state.{ListProperty, ReadOnlyProperty}
import org.scalajs.dom.{Comment, Node, console, window}


class Conditional(val condition: ReadOnlyProperty[Boolean]) extends NodeComponent[Comment], FormSubtreeRegistration {

  val thenChildrenProperty: ListProperty[ElementComponent[? <: Node]] =
    new ListProperty[ElementComponent[? <: Node]]()

  val elseChildrenProperty: ListProperty[ElementComponent[? <: Node]] =
    new ListProperty[ElementComponent[? <: Node]]()

  private val ifAnchor: Comment = newComment("jfx:if")
  private val elseAnchor: Comment = newComment("jfx:else")
  private val endAnchor: Comment = newComment("jfx:endif")

  private var mountedThen: List[ElementComponent[? <: Node]] = Nil
  private var mountedElse: List[ElementComponent[? <: Node]] = Nil

  override lazy val element: Comment = ifAnchor

  private var disposed: Boolean = false
  private var lastParent: Node | Null = null

  private val conditionObserver = condition.observe { showThen =>
    render(showThen)
  }
  disposable.add(conditionObserver)

  private val thenObserver = thenChildrenProperty.observeChanges { _ =>
    if (condition.get) renderThen(show = true)
  }
  disposable.add(thenObserver)

  private val elseObserver = elseChildrenProperty.observeChanges { _ =>
    if (!condition.get) renderElse(show = true)
  }
  disposable.add(elseObserver)

  override def onMount(): Unit = {
    if (disposed) return

    val parent = ifAnchor.parentNode
    if (parent != lastParent) {
      lastParent = parent
      ensureScaffold()
      render(condition.get)
    } else if (parent != null) {
      ensureScaffold()
    }
  }

  def thenAdd(child: ElementComponent[? <: Node]): Unit =
    thenChildrenProperty += child

  def elseAdd(child: ElementComponent[? <: Node]): Unit =
    elseChildrenProperty += child

  override def dispose(): Unit = {
    disposed = true

    // detach from DOM & form first (without disposing branch components yet)
    forceDetachMounted()
    removeDomNode(elseAnchor)
    removeDomNode(endAnchor)

    // dispose unique children from both branches
    val all = (thenChildrenProperty.toList ++ elseChildrenProperty.toList).distinct
    all.foreach(_.dispose())

    // stop observers and clean up
    disposable.dispose()
    thenChildrenProperty.clear()
    elseChildrenProperty.clear()
  }

  private def render(showThen: Boolean): Unit = {
    ensureScaffold()
    renderThen(show = showThen)
    renderElse(show = !showThen)
  }


  private def ensureScaffold(): Unit = {
    val parent = ifAnchor.parentNode
    if (parent == null) return

    if (elseAnchor.parentNode != parent) {
      forceDetachMounted()
      parent.insertBefore(elseAnchor, ifAnchor.nextSibling)
    }

    if (endAnchor.parentNode != parent) {
      forceDetachMounted()
      parent.insertBefore(endAnchor, elseAnchor.nextSibling)
    }

    if (!isAfter(elseAnchor, ifAnchor)) {
      forceDetachMounted()
      parent.insertBefore(elseAnchor, ifAnchor.nextSibling)
    }

    if (!isAfter(endAnchor, elseAnchor)) {
      forceDetachMounted()
      parent.insertBefore(endAnchor, elseAnchor.nextSibling)
    }
  }

  private def renderThen(show: Boolean): Unit = {
    val parent = ifAnchor.parentNode
    if (parent == null) return

    unmountThen()
    clearBetween(ifAnchor, elseAnchor, parent)
    if (!show) return

    val children = thenChildrenProperty.toList
    mount(children, parent, before = elseAnchor)
    mountedThen = children
  }

  private def renderElse(show: Boolean): Unit = {
    val parent = ifAnchor.parentNode
    if (parent == null) return

    unmountElse()
    clearBetween(elseAnchor, endAnchor, parent)
    if (!show) return

    val children = elseChildrenProperty.toList
    mount(children, parent, before = endAnchor)
    mountedElse = children
  }

  private def mount(children: List[ElementComponent[? <: Node]], parent: Node, before: Node): Unit = {
    children.foreach { child =>
      val oldParent = child.parent
      if (oldParent.exists(_ != this)) {
        console.warn("Conditional: child already has a different parent; moving DOM node anyway.")
      }
      parent.insertBefore(child.element, before)
      child.parent = Some(this)
      registerSubtree(child)
    }
  }

  private def unmountThen(): Unit = {
    mountedThen.foreach { child =>
      if (child.parent.contains(this)) child.parent = None
      unregisterSubtree(child)
    }
    mountedThen = Nil
  }

  private def unmountElse(): Unit = {
    mountedElse.foreach { child =>
      if (child.parent.contains(this)) child.parent = None
      unregisterSubtree(child)
    }
    mountedElse = Nil
  }

  private def forceDetachMounted(): Unit = {
    val allMounted = mountedThen ++ mountedElse
    allMounted.foreach { child =>
      if (child.parent.contains(this)) child.parent = None
      unregisterSubtree(child)
      removeDomNode(child.element)
    }
    mountedThen = Nil
    mountedElse = Nil
  }

  private def clearBetween(start: Node, end: Node, parent: Node): Unit = {
    var maybeNode: Node | Null = start.nextSibling
    while (maybeNode != null && maybeNode != end) {
      val node = maybeNode.asInstanceOf[Node]
      maybeNode = node.nextSibling
      parent.removeChild(node)
    }
  }

  private def removeDomNode(node: Node): Unit = {
    val parent = node.parentNode
    if (parent != null) parent.removeChild(node)
  }

  private def isAfter(node: Node, ref: Node): Boolean = {
    var cursor: Node | Null = ref.nextSibling
    while (cursor != null) {
      if (cursor == node) return true
      cursor = cursor.asInstanceOf[Node].nextSibling
    }
    false
  }

}
