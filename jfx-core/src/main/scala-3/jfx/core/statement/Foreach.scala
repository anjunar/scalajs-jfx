package jfx.core.statement

import jfx.core.component.{AbstractComponent, AbstractCustomComponent, Runtime}
import jfx.core.dsl.DslLayerTwo
import jfx.core.render.{Cursor, HostNode, VirtualHost}
import jfx.core.state.{ListProperty, ReadOnlyProperty}

import scala.collection.mutable
import scala.scalajs.js

class Foreach[V](items: ListProperty[V],
                 build: (V, Int) => AbstractComponent ?=> Cursor ?=> Unit,
                 reindexOnStructuralChange: Boolean = false) extends AbstractCustomComponent {
  import ListProperty.*

  private val mounted = mutable.ArrayBuffer.empty[ForeachItem[V]]
  private var initialized = false
  private var mountedCursor: Cursor = _

  override def compose(cursor: Cursor): Unit = {
    mountedCursor = cursor
    resetAll(cursor)
    initialized = true
    addDisposable(items.observeChanges(sync))
  }

  private def sync(change: Change[V]): Unit =
    change match {
      case Reset(_) =>
        resetAll(mountedCursor)
      case Add(element, _) =>
        mountAt(mounted.length, element, mountedCursor)
      case Insert(index, element, _) =>
        if (reindexOnStructuralChange) rebuildFrom(index)
        else mountAt(index, element, mountedCursor)
      case InsertAll(index, elements, _) =>
        if (reindexOnStructuralChange) rebuildFrom(index)
        else elements.toSeq.zipWithIndex.foreach { case (element, offset) => mountAt(index + offset, element, mountedCursor) }
      case RemoveAt(index, _, _) =>
        if (reindexOnStructuralChange) rebuildFrom(index)
        else unmountAt(index)
      case RemoveRange(index, elements, _) =>
        if (reindexOnStructuralChange) rebuildFrom(index)
        else unmountRange(index, elements.length)
      case UpdateAt(index, _, newElement, _) =>
        replaceAt(index, newElement)
      case Patch(from, removed, inserted, _) =>
        if (reindexOnStructuralChange) rebuildFrom(from)
        else {
          unmountRange(from, removed.length)
          inserted.toSeq.zipWithIndex.foreach { case (element, offset) => mountAt(from + offset, element, mountedCursor) }
        }
      case Clear(_, _) =>
        clearMounted()
    }

  private def resetAll(cursor: Cursor): Unit = {
    clearMounted()
    items.get.toSeq.zipWithIndex.foreach { case (value, index) => mountAt(index, value, cursor) }
  }

  private def rebuildFrom(index: Int): Unit = {
    val from = index.max(0).min(mounted.length)
    val count = mounted.length - from

    unmountRange(from, count)

    items.get.toSeq.drop(from).zipWithIndex.foreach { case (value, offset) =>
      mountAt(from + offset, value, mountedCursor)
    }
  }

  private def replaceAt(index: Int, value: V): Unit =
    if (index >= 0 && index < mounted.length) {
      unmountAt(index)
      mountAt(index, value, mountedCursor)
    } else {
      resetAll(mountedCursor)
    }

  private def mountAt(index: Int, value: V, cursor: Cursor): Unit = {
    val safeIndex = index.max(0).min(mounted.length)
    val item = new ForeachItem(value, safeIndex, build)

    Runtime.mount(item, insertionCursorAt(safeIndex, cursor), Some(this))
    mounted.insert(safeIndex, item)
    syncChildOrder()
  }

  private def unmountAt(index: Int): Unit =
    if (index >= 0 && index < mounted.length) {
      val item = mounted.remove(index)
      Runtime.unmount(item)
      syncChildOrder()
    } else {
      resetAll(mountedCursor)
    }

  private def unmountRange(index: Int, count: Int): Unit = {
    val safeIndex = index.max(0).min(mounted.length)
    val safeCount = count.max(0).min(mounted.length - safeIndex)

    (0 until safeCount).foreach(_ => unmountAt(safeIndex))
  }

  private def clearMounted(): Unit = {
    mounted.toVector.foreach(Runtime.unmount)
    mounted.clear()
    syncChildOrder()
  }

  private def syncChildOrder(): Unit = {
    _children.clear()
    _children ++= mounted
  }

  private def insertionCursorAt(index: Int, cursor: Cursor): Cursor =
    if (!initialized) cursor
    else mounted.lift(index).flatMap(firstHost).map(cursor.before).getOrElse(endCursor(cursor))

  private def firstHost(component: AbstractComponent): Option[HostNode] =
    component.physicalHosts.headOption

  private def endCursor(cursor: Cursor): Cursor =
    _host match {
      case host: VirtualHost =>
        host.end match {
          case Some(end) => cursor.before(end)
          case None => host.cursor.getOrElse(cursor)
        }
      case _ => cursor
    }
}

object Foreach {
  def foreach[V](items: ListProperty[V])(body: V => AbstractComponent ?=> Cursor ?=> Unit)
                (using AbstractComponent, Cursor): Foreach[V] =
    DslLayerTwo.child(new Foreach(items, (value, _) => body(value))) {}

  def foreach[V](items: ReadOnlyProperty[Seq[V]])(body: V => AbstractComponent ?=> Cursor ?=> Unit)
                (using AbstractComponent, Cursor): Foreach[V] =
    DslLayerTwo.child(new PropertyForeach(items, listOf(items.get), (value, _) => body(value), reindexOnStructuralChange = false)) {}

  def foreach[V](items: Seq[V])(body: V => AbstractComponent ?=> Cursor ?=> Unit)
                (using AbstractComponent, Cursor): Foreach[V] =
    foreach(listOf(items))(body)

  def foreachIndexed[V](items: ListProperty[V])(body: (V, Int) => AbstractComponent ?=> Cursor ?=> Unit)
                       (using AbstractComponent, Cursor): Foreach[V] =
    DslLayerTwo.child(new Foreach(items, body, reindexOnStructuralChange = true)) {}

  def foreachIndexed[V](items: ReadOnlyProperty[Seq[V]])(body: (V, Int) => AbstractComponent ?=> Cursor ?=> Unit)
                       (using AbstractComponent, Cursor): Foreach[V] =
    DslLayerTwo.child(new PropertyForeach(items, listOf(items.get), body, reindexOnStructuralChange = true)) {}

  def foreachIndexed[V](items: Seq[V])(body: (V, Int) => AbstractComponent ?=> Cursor ?=> Unit)
                       (using AbstractComponent, Cursor): Foreach[V] =
    foreachIndexed(listOf(items))(body)

  private def listOf[V](items: Seq[V]): ListProperty[V] =
    ListProperty(js.Array(items.toSeq*))
}
