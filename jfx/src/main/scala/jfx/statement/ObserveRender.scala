package jfx.statement

import jfx.core.component.{ElementComponent, FormSubtreeRegistration, NodeComponent}
import jfx.core.state.ReadOnlyProperty
import jfx.dsl.{ComponentContext, DslRuntime, Scope}
import org.scalajs.dom.{Comment, Node, console}

import scala.collection.mutable

class ObserveRender[T](
  val source: ReadOnlyProperty[T],
  private val renderBlock: T => Unit,
  private val renderScope: Scope,
  private val renderContext: ComponentContext
) extends NodeComponent[Comment], FormSubtreeRegistration {

  private val startAnchor: Comment = newComment("jfx:observe")
  private val endAnchor: Comment = newComment("jfx:endobserve")

  override val element: Comment = startAnchor

  private var renderedChildren: List[ElementComponent[? <: Node]] = Nil
  private var disposed: Boolean = false
  private var lastParent: Node | Null = null
  private var dirtyWhileUnmounted: Boolean = true
  private var hasRendered: Boolean = false

  private val sourceObserver = source.observe { _ =>
    if (!disposed) {
      val parent = startAnchor.parentNode
      if (parent == null) {
        dirtyWhileUnmounted = true
      } else {
        ensureScaffold(parent)
        rebuild(parent, source.get)
        dirtyWhileUnmounted = false
      }
    }
  }
  disposable.add(sourceObserver)

  override protected def mountContent(): Unit = {
    if (disposed) return

    val parent = startAnchor.parentNode
    if (parent != lastParent) {
      lastParent = parent
      if (parent == null) {
        detachRendered(removeEndAnchor = true, keepComponents = true)
      } else {
        ensureScaffold(parent)
        if (dirtyWhileUnmounted || !hasRendered) {
          rebuild(parent, source.get)
          dirtyWhileUnmounted = false
        } else if (needsReattach(parent)) {
          reattachRendered(parent)
        }
      }
    } else if (parent != null) {
      ensureScaffold(parent)
      if (dirtyWhileUnmounted || !hasRendered) {
        rebuild(parent, source.get)
        dirtyWhileUnmounted = false
      } else if (needsReattach(parent)) {
        reattachRendered(parent)
      }
    }
  }

  override def dispose(): Unit = {
    if (disposed) return
    disposed = true

    disposable.dispose()
    detachRendered(removeEndAnchor = true, keepComponents = false)
  }

  private def rebuild(parent: Node, value: T): Unit = {
    val nextChildren = buildChildren(value)

    detachRendered(removeEndAnchor = false, keepComponents = false)
    clearBetween(startAnchor, endAnchor, parent)

    renderedChildren = nextChildren
    hasRendered = true
    mountChildren(parent, nextChildren)
  }

  private def buildChildren(value: T): List[ElementComponent[? <: Node]] = {
    val children = mutable.ListBuffer.empty[ElementComponent[? <: Node]]

    DslRuntime.withComponentContext(
      DslRuntime.branchContext(renderContext, "observeRender", child => children += child)
    ) {
      given Scope = renderScope
      renderBlock(value)
    }

    children.toList
  }

  private def ensureScaffold(parent: Node): Unit = {
    if (endAnchor.parentNode != parent || !isAfter(endAnchor, startAnchor)) {
      detachRendered(removeEndAnchor = true, keepComponents = true)
      parent.insertBefore(endAnchor, startAnchor.nextSibling)
    }
  }

  private def mountChildren(parent: Node, children: List[ElementComponent[? <: Node]]): Unit = {
    children.foreach { child =>
      val oldParent = child.parent
      if (oldParent.exists(_ != this)) {
        console.warn("ObserveRender: child already has a different parent; moving DOM node anyway.")
      }

      parent.insertBefore(child.element, endAnchor)
      child.parent = Some(this)
      child.onMount()
      registerSubtree(child)
    }
  }

  private def reattachRendered(parent: Node): Unit =
    mountChildren(parent, renderedChildren)

  private def detachRendered(removeEndAnchor: Boolean, keepComponents: Boolean): Unit = {
    renderedChildren.foreach { child =>
      if (child.parent.contains(this)) {
        unregisterSubtree(child)
        child.onUnmount()
        child.parent = None
      }
      removeDomNode(child.element)
      if (!keepComponents) child.dispose()
    }

    if (!keepComponents) renderedChildren = Nil

    if (removeEndAnchor) removeDomNode(endAnchor)
  }

  private def needsReattach(parent: Node): Boolean =
    renderedChildren.exists(child => child.element.parentNode != parent || !child.parent.contains(this))

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

object ObserveRender {

  def apply[T](source: ReadOnlyProperty[T])(render: T => Unit)(using Scope): ObserveRender[T] =
    observeRender(source)(render)

  def observeRender[T](source: ReadOnlyProperty[T])(render: T => Unit): ObserveRender[T] =
    DslRuntime.currentScope { currentScope =>
      val currentContext = DslRuntime.currentComponentContext()
      val component = new ObserveRender(source, render, currentScope, currentContext)
      DslRuntime.attach(component, currentContext)
      component
    }

  def observeRenderValue[T](using observeRender: ObserveRender[T]): T =
    observeRender.source.get
}
