package jfx.core.layout

import jfx.core.component.{AbstractComponent, AbstractCustomComponent, Runtime}
import jfx.core.dsl.DslLayerTwo
import jfx.core.render.{Cursor, VirtualHost}
import jfx.core.state.{ListProperty, Property, ReadOnlyProperty}

import scala.collection.mutable

class Foreach[V](items: ReadOnlyProperty[Seq[V]],
                 build: (V, Int) => AbstractComponent ?=> Cursor ?=> Unit) extends AbstractCustomComponent {
  private val mounted = mutable.ArrayBuffer.empty[AbstractComponent]
  private var initialized = false

  override def compose(cursor: Cursor): Unit =
    addDisposable(items.observe(values => sync(values, cursor)))

  private def sync(values: Seq[V], cursor: Cursor): Unit = {
    clearMounted()

    val currentCursor =
      if (initialized) insertionCursor(cursor)
      else cursor

    values.zipWithIndex.foreach { case (value, index) =>
      val childOffset = _children.length

      given AbstractComponent = this
      given Cursor = currentCursor

      build(value, index)

      mounted ++= _children.drop(childOffset)
    }

    initialized = true
  }

  private def clearMounted(): Unit = {
    mounted.toVector.foreach(Runtime.unmount)
    mounted.clear()
  }

  private def insertionCursor(cursor: Cursor): Cursor =
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
  def foreach[V](items: ReadOnlyProperty[Seq[V]])(body: V => AbstractComponent ?=> Cursor ?=> Unit)
                (using AbstractComponent, Cursor): Foreach[V] =
    foreachIndexed(items)((value, _) => body(value))

  def foreach[V](items: ListProperty[V])(body: V => AbstractComponent ?=> Cursor ?=> Unit)
                (using AbstractComponent, Cursor): Foreach[V] =
    foreach(items.asProperty.map(_.toSeq))(body)

  def foreach[V](items: Seq[V])(body: V => AbstractComponent ?=> Cursor ?=> Unit)
                (using AbstractComponent, Cursor): Foreach[V] =
    foreach(Property(items))(body)

  def foreachIndexed[V](items: ReadOnlyProperty[Seq[V]])(body: (V, Int) => AbstractComponent ?=> Cursor ?=> Unit)
                       (using AbstractComponent, Cursor): Foreach[V] =
    DslLayerTwo.child(new Foreach(items, body)) {}

  def foreachIndexed[V](items: ListProperty[V])(body: (V, Int) => AbstractComponent ?=> Cursor ?=> Unit)
                       (using AbstractComponent, Cursor): Foreach[V] =
    foreachIndexed(items.asProperty.map(_.toSeq))(body)

  def foreachIndexed[V](items: Seq[V])(body: (V, Int) => AbstractComponent ?=> Cursor ?=> Unit)
                       (using AbstractComponent, Cursor): Foreach[V] =
    foreachIndexed(Property(items))(body)
}
