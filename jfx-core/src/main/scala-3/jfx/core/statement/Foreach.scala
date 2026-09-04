package jfx.core.statement

import jfx.core.component.{AbstractComponent, AbstractCustomComponent, DynamicMountPoint, Runtime}
import jfx.core.dsl.DslLayer
import jfx.core.render.{Cursor, HostNode}
import jfx.core.state.{ListProperty, ReadOnlyProperty}

import scala.collection.mutable
import scala.scalajs.js

class Foreach[V](
    items: ListProperty[V],
    build: (V, Int) => AbstractComponent ?=> Cursor ?=> Unit,
    reindexOnStructuralChange: Boolean = false,
    preserveUpdates: Boolean = false
) extends AbstractCustomComponent {
  import ListProperty.*

  private val mounted                       = mutable.ArrayBuffer.empty[ForeachItem[V]]
  private var mountPoint: DynamicMountPoint = _

  override def compose(cursor: Cursor): Unit = {
    mountPoint = new DynamicMountPoint(this, cursor)
    resetAll()
    mountPoint.finishInitialComposition()
    addDisposable(items.observeChanges(sync))
  }

  private def sync(change: Change[V]): Unit =
    change match {
      case Reset(_) =>
        resetAll()
      case Add(element, _) =>
        mountAt(mounted.length, element)
      case Insert(index, element, _) =>
        if (reindexOnStructuralChange) rebuildFrom(index)
        else mountAt(index, element)
      case InsertAll(index, elements, _) =>
        if (reindexOnStructuralChange) rebuildFrom(index)
        else
          elements.toSeq.zipWithIndex.foreach { case (element, offset) =>
            mountAt(index + offset, element)
          }
      case RemoveAt(index, _, _) =>
        if (reindexOnStructuralChange) rebuildFrom(index)
        else unmountAt(index)
      case RemoveRange(index, elements, _) =>
        if (reindexOnStructuralChange) rebuildFrom(index)
        else unmountRange(index, elements.length)
      case UpdateAt(index, _, newElement, _) =>
        if (preserveUpdates) updateInPlace(index, newElement)
        else replaceAt(index, newElement)
      case Patch(from, removed, inserted, _) =>
        if (reindexOnStructuralChange) rebuildFrom(from)
        else {
          unmountRange(from, removed.length)
          inserted.toSeq.zipWithIndex.foreach { case (element, offset) =>
            mountAt(from + offset, element)
          }
        }
      case Clear(_, _) =>
        clearMounted()
    }

  private def resetAll(): Unit = {
    clearMounted()
    items.get.toSeq.zipWithIndex.foreach { case (value, index) => mountAt(index, value) }
  }

  private def rebuildFrom(index: Int): Unit = {
    val from  = index.max(0).min(mounted.length)
    val count = mounted.length - from

    unmountRange(from, count)

    items.get.toSeq.drop(from).zipWithIndex.foreach { case (value, offset) =>
      mountAt(from + offset, value)
    }
  }

  private def replaceAt(index: Int, value: V): Unit =
    if (index >= 0 && index < mounted.length) {
      unmountAt(index)
      mountAt(index, value)
    } else {
      resetAll()
    }

  private def updateInPlace(index: Int, value: V): Unit =
    if (index >= 0 && index < mounted.length) mounted(index).updateValue(value)
    else resetAll()

  private def mountAt(index: Int, value: V): Unit = {
    val safeIndex = index.max(0).min(mounted.length)
    val item      = new ForeachItem(value, safeIndex, build)

    // Pass the position to Runtime instead of correcting the children list afterwards. See
    // CHANGE.md P4-3.
    Runtime.mount(item, insertionCursorAt(safeIndex), Some(this), Some(safeIndex))
    mounted.insert(safeIndex, item)
  }

  private def unmountAt(index: Int): Unit =
    if (index >= 0 && index < mounted.length) {
      val item = mounted.remove(index)
      // Runtime.unmount removes the child from the children list itself.
      Runtime.unmount(item)
    } else {
      resetAll()
    }

  private def unmountRange(index: Int, count: Int): Unit = {
    val safeIndex = index.max(0).min(mounted.length)
    val safeCount = count.max(0).min(mounted.length - safeIndex)

    (0 until safeCount).foreach(_ => unmountAt(safeIndex))
  }

  private def clearMounted(): Unit = {
    mounted.toVector.foreach(Runtime.unmount)
    mounted.clear()
  }

  private def insertionCursorAt(index: Int): Cursor =
    mountPoint.cursorBefore(mounted.lift(index).flatMap(firstHost))

  private def firstHost(component: AbstractComponent): Option[HostNode] =
    component.firstPhysicalHost

}

object Foreach {
  def foreach[V](items: ListProperty[V])(
      body: V => AbstractComponent ?=> Cursor ?=> Unit
  )(using AbstractComponent, Cursor): Foreach[V] =
    DslLayer.child(new Foreach(items, (value, _) => body(value))) {}

  def foreach[V](
      items: ReadOnlyProperty[Seq[V]]
  )(body: V => AbstractComponent ?=> Cursor ?=> Unit)(using AbstractComponent, Cursor): Foreach[V] =
    DslLayer.child(
      new PropertyForeach(
        items,
        listOf(items.get),
        (value, _) => body(value),
        reindexOnStructuralChange = false
      )
    ) {}

  def foreach[V](items: Seq[V])(
      body: V => AbstractComponent ?=> Cursor ?=> Unit
  )(using AbstractComponent, Cursor): Foreach[V] =
    foreach(listOf(items))(body)

  def foreachIndexed[V](items: ListProperty[V])(
      body: (V, Int) => AbstractComponent ?=> Cursor ?=> Unit
  )(using AbstractComponent, Cursor): Foreach[V] =
    DslLayer.child(new Foreach(items, body, reindexOnStructuralChange = true)) {}

  private[jfx] def foreachIndexedPreservingUpdates[V](items: ListProperty[V])(
      body: (V, Int) => AbstractComponent ?=> Cursor ?=> Unit
  )(using AbstractComponent, Cursor): Foreach[V] =
    DslLayer.child(
      new Foreach(items, body, reindexOnStructuralChange = true, preserveUpdates = true)
    ) {}

  def foreachIndexed[V](items: ReadOnlyProperty[Seq[V]])(
      body: (V, Int) => AbstractComponent ?=> Cursor ?=> Unit
  )(using AbstractComponent, Cursor): Foreach[V] =
    DslLayer.child(
      new PropertyForeach(items, listOf(items.get), body, reindexOnStructuralChange = true)
    ) {}

  def foreachIndexed[V](items: Seq[V])(
      body: (V, Int) => AbstractComponent ?=> Cursor ?=> Unit
  )(using AbstractComponent, Cursor): Foreach[V] =
    foreachIndexed(listOf(items))(body)

  private def listOf[V](items: Seq[V]): ListProperty[V] =
    ListProperty(js.Array(items.toSeq*))
}
