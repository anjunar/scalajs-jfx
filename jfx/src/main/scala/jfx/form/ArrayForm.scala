package jfx.form

import jfx.core.component.NativeComponent
import jfx.core.component.FormRegistrationBoundary
import jfx.core.state.{Disposable, ListProperty, Property}
import jfx.core.state.ListProperty.*
import org.scalajs.dom.HTMLFieldSetElement

import scala.compiletime.uninitialized
import scala.scalajs.js

class ArrayForm[V <: Model[V]](val name: String)
    extends NativeComponent[HTMLFieldSetElement],
      Control[js.Array[V], HTMLFieldSetElement] {

  override val element: HTMLFieldSetElement = newElement("fieldset")

  override val valueProperty: ListProperty[V] = new ListProperty[V]()

  private val itemsObserver: Disposable =
    valueProperty.observeChanges(onItemsChange)
  disposable.add(itemsObserver)

  private var mounted: Vector[Control[?, ?]] = Vector.empty

  private var pendingRebuild: Boolean = false

  var controlRenderer: Int => Control[?, ?] = uninitialized

  def addControlRenderer(control : Int => Control[?,?]): Unit = {
    controlRenderer = control
    pendingRebuild = false
    rebuildAll()
  }

  private def onItemsChange(change: ListProperty.Change[V]): Unit = {
    if (controlRenderer == null) {
      pendingRebuild = true
      return
    }

    change match {
      case Reset(_) =>
        rebuildAll()

      case Add(_, items) =>
        addAtEnd(items)

      case Insert(index, _, items) =>
        rebuildFrom(items, index)

      case InsertAll(index, _, items) =>
        rebuildFrom(items, index)

      case RemoveAt(index, _, items) =>
        rebuildFrom(items, index)

      case RemoveRange(index, _, items) =>
        rebuildFrom(items, index)

      case UpdateAt(index, _, _, items) =>
        replaceAt(items, index)

      case Patch(from, _, _, items) =>
        rebuildFrom(items, from)

      case Clear(_, _) =>
        rebuildAll()
    }
  }

  private def rebuildAll(): Unit = {
    if (controlRenderer == null) {
      pendingRebuild = true
      return
    }

    rebuildFrom(valueProperty, 0)
  }

  private def addAtEnd(items: ListProperty[V]): Unit = {
    val index = items.length - 1
    if (index < 0) return

    // If we are out of sync for any reason, do a full rebuild.
    if (mounted.length != index || childrenProperty.length != index) {
      rebuildFrom(items, 0)
      return
    }

    val child = buildChild(items, index)
    mounted = mounted :+ child
    childrenProperty += child
  }

  private def replaceAt(items: ListProperty[V], index: Int): Unit = {
    if (index < 0 || index >= items.length) return
    if (mounted.length != items.length || childrenProperty.length != mounted.length) {
      rebuildFrom(items, 0)
      return
    }

    val child = buildChild(items, index)
    mounted = mounted.updated(index, child)
    childrenProperty.update(index, child)
  }

  private def rebuildFrom(items: ListProperty[V], fromIndex: Int): Unit = {
    val from = math.max(0, fromIndex)

    val n = items.length
    val prefix = mounted.take(from)
    val replaced = mounted.length - prefix.length

    val newTail = Vector.newBuilder[Control[?, ?]]
    var i = from
    while (i < n) {
      newTail += buildChild(items, i)
      i += 1
    }

    val newTailVec = newTail.result()
    mounted = prefix ++ newTailVec

    // Keep DOM in sync via NativeComponent list diffs.
    childrenProperty.patchInPlace(from, newTailVec, replaced)
  }

  private def buildChild(items: ListProperty[V], index: Int): Control[?, ?] = {
    val child = controlRenderer(index)

    child match {
      case form: Formular[?, ?] =>
        form
          .asInstanceOf[Formular[V, ?]]
          .valueProperty
          .asInstanceOf[Property[V]]
          .set(items(index))
      case _ => ()
    }

    child
  }

}
