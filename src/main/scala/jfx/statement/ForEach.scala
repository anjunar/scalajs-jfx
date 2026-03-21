package jfx.statement

import jfx.core.component.{FormSubtreeRegistration, NodeComponent}
import jfx.core.state.ListProperty
import jfx.core.state.ListProperty.*
import org.scalajs.dom.{Comment, Node, console, window}

import scala.scalajs.js

class ForEach[T](
  val items: ListProperty[T],
  val renderItem: (T, Int) => NodeComponent[? <: Node]
) extends NodeComponent[Comment], FormSubtreeRegistration {

  private val startAnchor: Comment = newComment("jfx:foreach")
  private val endAnchor: Comment = newComment("jfx:endforeach")

  override lazy val element: Comment = startAnchor

  private var mounted: Vector[NodeComponent[? <: Node]] = Vector.empty

  private var disposed: Boolean = false
  private var lastParent: Node | Null = null
  private var dirtyWhileUnmounted: Boolean = false

  private val itemsObserver = items.observeChanges(onItemsChange)
  disposable.add(itemsObserver)

  override def onMount(): Unit = {
    if (!disposed) {
      val parent = startAnchor.parentNode
      if (parent != lastParent) {
        lastParent = parent
        if (parent == null) {
          detachAll(removeEndAnchor = true, keepComponents = true)
        } else {
          ensureScaffold(parent)
          if (dirtyWhileUnmounted || mounted.isEmpty) {
            rebuildAll(parent)
            dirtyWhileUnmounted = false
          } else {
            reattachAll(parent)
          }
        }
      } else if (parent != null) {
        ensureScaffold(parent)
      }
    }
  }

  override def dispose(): Unit = {
    disposed = true

    // stop observing first
    disposable.dispose()

    // detach & unregister
    detachAll(removeEndAnchor = true, keepComponents = false)
  }

  private def onItemsChange(change: ListProperty.Change[T]): Unit = {
    if (disposed) return

    val parent = startAnchor.parentNode
    if (parent == null) {
      // list changed while we're not mounted; reconcile on next mount
      dirtyWhileUnmounted = true
      return
    }

    ensureScaffold(parent)

    change match {
      case Reset(_) =>
        rebuildAll(parent)

      case Add(_, _) =>
        // only the last index is new; keep everything else
        addAtEnd(parent)

      case Insert(index, _, _) =>
        rebuildFrom(parent, index)

      case InsertAll(index, _, _) =>
        rebuildFrom(parent, index)

      case RemoveAt(index, _, _) =>
        rebuildFrom(parent, index)

      case RemoveRange(index, _, _) =>
        rebuildFrom(parent, index)

      case UpdateAt(index, _, _, _) =>
        replaceAt(parent, index)

      case Patch(from, _, _, _) =>
        rebuildFrom(parent, from)

      case Clear(_, _) =>
        rebuildAll(parent)
    }
  }

  private def ensureScaffold(parent: Node): Unit = {
    if (endAnchor.parentNode != parent || !isAfter(endAnchor, startAnchor)) {
      // If we got moved, remove any old sibling nodes first to avoid artifacts.
      detachAll(removeEndAnchor = true, keepComponents = true)
      parent.insertBefore(endAnchor, startAnchor.nextSibling)
    }
  }

  private def addAtEnd(parent: Node): Unit = {
    val index = items.length - 1
    if (index < 0) return

    // In case we were out-of-sync, do a full rebuild.
    if (mounted.length != index) {
      rebuildAll(parent)
      return
    }

    val child = renderItem(items(index), index)
    mountChild(parent, before = endAnchor, child = child)
    mounted = mounted :+ child
  }

  private def rebuildAll(parent: Node): Unit =
    rebuildFrom(parent, 0)

  private def rebuildFrom(parent: Node, fromIndex: Int): Unit = {
    val clampedFrom = math.max(0, fromIndex)

    // dispose existing tail (and remove from DOM)
    val prefix = mounted.take(clampedFrom)
    val tail = mounted.drop(clampedFrom)
    tail.foreach(disposeChild)
    mounted = prefix

    // build new tail from current items
    val n = items.length
    var i = clampedFrom
    while (i < n) {
      val child = renderItem(items(i), i)
      mountChild(parent, before = endAnchor, child = child)
      mounted = mounted :+ child
      i += 1
    }
  }

  private def replaceAt(parent: Node, index: Int): Unit = {
    if (index < 0 || index >= items.length) return
    if (mounted.length != items.length) {
      rebuildAll(parent)
      return
    }

    val oldChild = mounted(index)
    val newChild = renderItem(items(index), index)

    parent.insertBefore(newChild.element, oldChild.element)
    newChild.parent = Some(this)
    registerSubtree(newChild)

    disposeChild(oldChild)

    mounted = mounted.updated(index, newChild)
  }

  private def reattachAll(parent: Node): Unit = {
    mounted.foreach { child =>
      parent.insertBefore(child.element, endAnchor)
      child.parent = Some(this)
      registerSubtree(child)
    }
  }

  private def detachAll(removeEndAnchor: Boolean, keepComponents: Boolean): Unit = {
    mounted.foreach { child =>
      if (child.parent.contains(this)) child.parent = None
      unregisterSubtree(child)
      removeDomNode(child.element)
      if (!keepComponents) child.dispose()
    }

    if (!keepComponents) mounted = Vector.empty

    if (removeEndAnchor) removeDomNode(endAnchor)
  }

  private def mountChild(parent: Node, before: Node, child: NodeComponent[? <: Node]): Unit = {
    val oldParent = child.parent
    if (oldParent.exists(_ != this)) {
      console.warn("ForEach: child already has a different parent; moving DOM node anyway.")
    }

    parent.insertBefore(child.element, before)
    child.parent = Some(this)
    child.onMount()
    registerSubtree(child)
  }

  private def disposeChild(child: NodeComponent[? <: Node]): Unit = {
    if (child.parent.contains(this)) child.parent = None
    unregisterSubtree(child)
    removeDomNode(child.element)
    child.dispose()
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

object ForEach {
  def apply[T](items: ListProperty[T])(renderItem: (T, Int) => NodeComponent[? <: Node]): ForEach[T] =
    new ForEach(items, renderItem)
}
