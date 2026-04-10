package jfx.statement

import jfx.core.component.{FormSubtreeRegistration, NodeComponent}
import jfx.core.state.ListProperty
import jfx.core.state.ListProperty.*
import jfx.dsl.{ComponentContext, DslRuntime, Scope}
import org.scalajs.dom.{Comment, Node, console, window}

import scala.scalajs.js

class ForEach[T](
  val items: ListProperty[T],
  val renderItem: (T, Int) => NodeComponent[? <: Node],
  private val renderScope: Scope,
  private val renderContext: ComponentContext
) extends NodeComponent[Comment], FormSubtreeRegistration {

  private val startAnchor: Comment = newComment("jfx:foreach")
  private val endAnchor: Comment = newComment("jfx:endforeach")

  override val element: Comment = startAnchor

  private var mounted: Vector[NodeComponent[? <: Node]] = Vector.empty

  private var disposed: Boolean = false
  private var lastParent: Node | Null = null
  private var dirtyWhileUnmounted: Boolean = false

  private val itemsObserver = items.observeChanges(onItemsChange)
  disposable.add(itemsObserver)

  override protected def mountContent(): Unit = {
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
        addAtEnd(parent)

      case Insert(index, _, _) =>
        insertAt(parent, index)

      case InsertAll(index, elements, _) =>
        insertAllAt(parent, index, elements.length)

      case RemoveAt(index, _, _) =>
        removeAt(index)

      case RemoveRange(index, removed, _) =>
        removeRange(index, removed.length)

      case UpdateAt(index, _, _, _) =>
        replaceAt(parent, index)

      case Patch(from, removed, inserted, _) =>
        patchAt(parent, from, removed.length, inserted.length)

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

    val child = buildChild(items(index), index)
    mountChild(parent, before = endAnchor, child = child)
    mounted = mounted :+ child
  }

  private def insertAt(parent: Node, index: Int): Unit = {
    val clampedIndex = math.max(0, math.min(index, items.length - 1))
    if (mounted.length != items.length - 1) {
      rebuildAll(parent)
      return
    }

    val child = buildChild(items(clampedIndex), clampedIndex)
    val before = mounted.lift(clampedIndex).map(_.element).getOrElse(endAnchor)
    mountChild(parent, before = before, child = child)
    mounted = mounted.patch(clampedIndex, Seq(child), 0)
  }

  private def insertAllAt(parent: Node, index: Int, count: Int): Unit = {
    if (count <= 0) return
    if (mounted.length != items.length - count) {
      rebuildAll(parent)
      return
    }

    val clampedIndex = math.max(0, math.min(index, items.length))
    val before = mounted.lift(clampedIndex).map(_.element).getOrElse(endAnchor)
    val inserted =
      (0 until count).map { offset =>
        val currentIndex = clampedIndex + offset
        val child = buildChild(items(currentIndex), currentIndex)
        mountChild(parent, before = before, child = child)
        child
      }

    mounted = mounted.patch(clampedIndex, inserted, 0)
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
      val child = buildChild(items(i), i)
      mountChild(parent, before = endAnchor, child = child)
      mounted = mounted :+ child
      i += 1
    }
  }

  private def removeAt(index: Int): Unit = {
    if (index < 0 || index >= mounted.length) {
      return
    }

    disposeChild(mounted(index))
    mounted = mounted.patch(index, Nil, 1)
  }

  private def removeRange(index: Int, count: Int): Unit = {
    if (count <= 0 || index < 0 || index >= mounted.length) return

    val clampedCount = math.min(count, mounted.length - index)
    mounted.slice(index, index + clampedCount).foreach(disposeChild)
    mounted = mounted.patch(index, Nil, clampedCount)
  }

  private def patchAt(parent: Node, from: Int, removedCount: Int, insertedCount: Int): Unit = {
    val canApplyIncrementally =
      mounted.length == items.length - insertedCount + removedCount

    if (!canApplyIncrementally) {
      rebuildFrom(parent, from)
      return
    }

    removeRange(from, removedCount)
    insertAllAt(parent, from, insertedCount)
  }

  private def replaceAt(parent: Node, index: Int): Unit = {
    if (index < 0 || index >= items.length) return
    if (mounted.length != items.length) {
      rebuildAll(parent)
      return
    }

    val oldChild = mounted(index)
    val newChild = buildChild(items(index), index)

    parent.insertBefore(newChild.element, oldChild.element)
    newChild.parent = Some(this)
    newChild.onMount()
    registerSubtree(newChild)

    disposeChild(oldChild)

    mounted = mounted.updated(index, newChild)
  }

  private def reattachAll(parent: Node): Unit = {
    mounted.foreach { child =>
      parent.insertBefore(child.element, endAnchor)
      child.parent = Some(this)
      child.onMount()
      registerSubtree(child)
    }
  }

  private def detachAll(removeEndAnchor: Boolean, keepComponents: Boolean): Unit = {
    mounted.foreach { child =>
      if (child.parent.contains(this)) {
        unregisterSubtree(child)
        child.onUnmount()
        child.parent = None
      }
      removeDomNode(child.element)
      if (!keepComponents) child.dispose()
    }

    if (!keepComponents) mounted = Vector.empty

    if (removeEndAnchor) removeDomNode(endAnchor)
  }

  private def buildChild(item: T, index: Int): NodeComponent[? <: Node] =
    DslRuntime.withScope(renderScope) {
      DslRuntime.withComponentContext(ComponentContext(None, renderContext.enclosingForm)) {
        renderItem(item, index)
      }
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
    if (child.parent.contains(this)) {
      unregisterSubtree(child)
      child.onUnmount()
      child.parent = None
    }
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
    DslRuntime.currentScope { currentScope =>
      val currentContext = DslRuntime.currentComponentContext()
      new ForEach(
        items,
        renderItem,
        currentScope,
        ComponentContext(None, currentContext.enclosingForm)
      )
    }

  def forEach[T](items: ListProperty[T])(renderItem: (T, Int) => NodeComponent[? <: Node]): ForEach[T] =
    DslRuntime.currentScope { currentScope =>
      val currentContext = DslRuntime.currentComponentContext()
      val component =
        new ForEach(
          items,
          renderItem,
          currentScope,
          ComponentContext(None, currentContext.enclosingForm)
        )
      DslRuntime.attach(component, currentContext)
      component
    }

  def forEach[T](items: ListProperty[T])(renderItem: T => NodeComponent[? <: Node]): ForEach[T] =
    forEach(items) { (item, _) => renderItem(item) }
}
