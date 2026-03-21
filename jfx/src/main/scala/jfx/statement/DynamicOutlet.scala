package jfx.statement

import jfx.core.component.{FormSubtreeRegistration, NodeComponent}
import jfx.core.state.ReadOnlyProperty
import jfx.dsl.DslRuntime
import org.scalajs.dom.{Comment, Node, console}

class DynamicOutlet(
  val content: ReadOnlyProperty[? <: NodeComponent[? <: Node] | Null]
) extends NodeComponent[Comment], FormSubtreeRegistration {

  private val startAnchor: Comment = newComment("jfx:outlet")
  private val endAnchor: Comment = newComment("jfx:endoutlet")

  override val element: Comment = startAnchor

  private var mounted: NodeComponent[? <: Node] | Null = null
  private var disposed: Boolean = false
  private var lastParent: Node | Null = null
  private var dirtyWhileUnmounted: Boolean = false

  private val contentObserver = content.observe { _ =>
    if (!disposed) {
      val parent = startAnchor.parentNode
      if (parent == null) {
        dirtyWhileUnmounted = true
      } else {
        ensureScaffold(parent)
        reconcile(parent)
        dirtyWhileUnmounted = false
      }
    }
  }
  disposable.add(contentObserver)

  override protected def mountContent(): Unit = {
    if (disposed) return

    val parent = startAnchor.parentNode
    if (parent != lastParent) {
      lastParent = parent
      if (parent == null) {
        detachMounted(removeEndAnchor = true)
      } else {
        ensureScaffold(parent)
        reconcile(parent)
        dirtyWhileUnmounted = false
      }
    } else if (parent != null) {
      ensureScaffold(parent)
      if (dirtyWhileUnmounted || needsReattach(parent)) {
        reconcile(parent)
        dirtyWhileUnmounted = false
      }
    }
  }

  override def dispose(): Unit = {
    if (disposed) return
    disposed = true

    disposable.dispose()
    detachMounted(removeEndAnchor = true)
  }

  private def reconcile(parent: Node): Unit = {
    val next = currentContent

    if (mounted eq next) {
      if (needsReattach(parent)) mount(parent, next)
      return
    }

    detachMounted(removeEndAnchor = false)
    mounted = null

    if (next != null) {
      mount(parent, next)
      mounted = next
    }
  }

  private def currentContent: NodeComponent[? <: Node] | Null =
    content.get.asInstanceOf[NodeComponent[? <: Node] | Null]

  private def ensureScaffold(parent: Node): Unit = {
    if (endAnchor.parentNode != parent || !isAfter(endAnchor, startAnchor)) {
      detachMounted(removeEndAnchor = true)
      parent.insertBefore(endAnchor, startAnchor.nextSibling)
    }
  }

  private def mount(parent: Node, child: NodeComponent[? <: Node] | Null): Unit = {
    if (child == null) return

    val oldParent = child.parent
    if (oldParent.exists(_ != this)) {
      console.warn("DynamicOutlet: child already has a different parent; moving DOM node anyway.")
    }

    parent.insertBefore(child.element, endAnchor)
    child.parent = Some(this)
    child.onMount()
    registerSubtree(child)
  }

  private def detachMounted(removeEndAnchor: Boolean): Unit = {
    val child = mounted
    if (child != null) {
      if (child.parent.contains(this)) {
        unregisterSubtree(child)
        child.onUnmount()
        child.parent = None
      }
      removeDomNode(child.element)
    }

    if (removeEndAnchor) removeDomNode(endAnchor)
  }

  private def needsReattach(parent: Node): Boolean = {
    val child = mounted
    child != null && (child.element.parentNode != parent || !child.parent.contains(this))
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

object DynamicOutlet {
  def apply(content: ReadOnlyProperty[? <: NodeComponent[? <: Node] | Null]): DynamicOutlet =
    new DynamicOutlet(content)

  def outlet(content: ReadOnlyProperty[? <: NodeComponent[? <: Node] | Null]): DynamicOutlet =
    DslRuntime.currentScope { _ =>
      val currentContext = DslRuntime.currentComponentContext()
      val component = new DynamicOutlet(content)
      DslRuntime.attach(component, currentContext)
      component
    }

  def dynamicOutlet(content: ReadOnlyProperty[? <: NodeComponent[? <: Node] | Null]): DynamicOutlet =
    outlet(content)

  def outletContent(using outlet: DynamicOutlet): ReadOnlyProperty[? <: NodeComponent[? <: Node] | Null] =
    outlet.content
}
