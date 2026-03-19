package jfx.core.state

import org.scalajs.dom.console

import scala.collection.mutable
import scala.scalajs.js
import scala.util.control.NonFatal

class ListProperty[V](val underlying: js.Array[V] = js.Array[V]()) extends ReadOnlyProperty[js.Array[V]], mutable.Buffer[V] {

  private val listeners = js.Array[js.Array[V] => Unit]()
  private val changeListeners = js.Array[ListProperty.Change[V] => Unit]()

  override def get: js.Array[V] = underlying

  private def notifyListeners(): Unit =
    listeners.toList.foreach(listener => listener(get))

  def notifiend(): Unit =
    notifiend(ListProperty.Reset(this))

  def notifiend(change: ListProperty.Change[V]): Unit = {
    changeListeners.toList.foreach(listener => listener(change))
    notifyListeners()
  }

  override def observe(listener: js.Array[V] => Unit): Disposable = {
    listeners += listener
    listener(get)

    if (listeners.size > 100) {
      console.warn("Too many listeners on ${this::class.simpleName} : ${listeners.size}")
    }

    () => listeners -= listener
  }

  def observeChanges(listener: ListProperty.Change[V] => Unit): Disposable = {
    changeListeners += listener

    if (changeListeners.size > 100) {
      console.warn("Too many listeners on ${this::class.simpleName} : ${changeListeners.size}")
    }

    () => changeListeners -= listener
  }

  override def prepend(elem: V): ListProperty.this.type = {
    insert(0, elem)
    this
  }

  override def insert(idx: Int, elem: V): Unit = {
    if (idx < 0 || idx > underlying.length) throw IndexOutOfBoundsException(s"$idx")
    underlying.splice(idx, 0, elem)
    notifiend(ListProperty.Insert(idx, elem, this))
  }

  override def insertAll(idx: Int, elems: IterableOnce[V]): Unit = {
    if (idx < 0 || idx > underlying.length) throw IndexOutOfBoundsException(s"$idx")
    val seq = elems.iterator.toSeq
    if (seq.isEmpty) return
    val inserted = js.Array(seq*)
    underlying.splice(idx, 0, seq*)
    notifiend(ListProperty.InsertAll(idx, inserted, this))
  }

  override def remove(idx: Int): V = {
    if (idx < 0 || idx >= underlying.length) throw IndexOutOfBoundsException(s"$idx")
    val removed = underlying.splice(idx, 1)
    val element = removed(0)
    notifiend(ListProperty.RemoveAt(idx, element, this))
    element
  }

  override def remove(idx: Int, count: Int): Unit = {
    if (count < 0) throw IllegalArgumentException(s"$count")
    if (idx < 0 || idx > underlying.length) throw IndexOutOfBoundsException(s"$idx")
    if (idx + count > underlying.length) throw IndexOutOfBoundsException(s"${idx + count}")
    if (count == 0) return
    val removed = underlying.splice(idx, count)
    notifiend(ListProperty.RemoveRange(idx, removed, this))
  }

  override def patchInPlace(from: Int, patch: IterableOnce[V], replaced: Int): ListProperty.this.type = {
    if (replaced < 0) throw IllegalArgumentException(s"$replaced")
    if (from < 0 || from > underlying.length) throw IndexOutOfBoundsException(s"$from")
    if (from + replaced > underlying.length) throw IndexOutOfBoundsException(s"${from + replaced}")

    val seq = patch.iterator.toSeq
    if (seq.isEmpty && replaced == 0) return this

    val inserted = js.Array(seq*)
    val removed = underlying.splice(from, replaced, seq*)
    notifiend(ListProperty.Patch(from, removed, inserted, this))
    this
  }

  override def addOne(elem: V): ListProperty.this.type = {
    underlying.push(elem)
    notifiend(ListProperty.Add(elem, this))
    this
  }

  override def clear(): Unit = {
    if (underlying.length == 0) return
    val removed = underlying.splice(0, underlying.length)
    notifiend(ListProperty.Clear(removed, this))
  }

  override def update(idx: Int, elem: V): Unit = {
    if (idx < 0 || idx >= underlying.length) throw IndexOutOfBoundsException(s"$idx")
    val oldElement = underlying(idx)
    if (oldElement == elem) return
    underlying(idx) = elem
    notifiend(ListProperty.UpdateAt(idx, oldElement, elem, this))
  }

  override def apply(i: Int): V = {
    if (i < 0 || i >= underlying.length) throw IndexOutOfBoundsException(s"$i")
    underlying(i)
  }

  override def length: Int = underlying.length

  override def iterator: Iterator[V] = new Iterator[V] {
    private var i = 0
    override def hasNext: Boolean = i < underlying.length
    override def next(): V = {
      if (!hasNext) throw new NoSuchElementException("next on empty iterator")
      val value = underlying(i)
      i += 1
      value
    }
  }
}

object ListProperty {

  def subscribeBidirectional[V](a: ListProperty[V], b: ListProperty[V]): Disposable = {
    if (a.eq(b)) return () => ()

    resetFrom(b, a)

    var settingA = false
    var settingB = false

    val da = a.observeChanges { change =>
      if (!settingA) {
        settingB = true
        try applyChange(source = a, target = b, change = change)
        finally settingB = false
      }
    }

    val db = b.observeChanges { change =>
      if (!settingB) {
        settingA = true
        try applyChange(source = b, target = a, change = change)
        finally settingA = false
      }
    }

    val composite = new CompositeDisposable()
    composite.add(da)
    composite.add(db)
    composite
  }

  private def resetFrom[V](target: ListProperty[V], source: ListProperty[V]): Unit = {
    val values = source.get.toSeq
    target.underlying.splice(0, target.underlying.length, values*)
    target.notifiend(Reset(target))
  }

  private def applyChange[V](source: ListProperty[V], target: ListProperty[V], change: Change[V]): Unit =
    try
      change match {
        case Reset(_) =>
          resetFrom(target, source)
        case Add(element, _) =>
          target.addOne(element)
        case Insert(index, element, _) =>
          target.insert(index, element)
        case InsertAll(index, elements, _) =>
          target.insertAll(index, elements.toSeq)
        case RemoveAt(index, _, _) =>
          target.remove(index)
        case RemoveRange(index, elements, _) =>
          target.remove(index, elements.length)
        case UpdateAt(index, _, newElement, _) =>
          target.update(index, newElement)
        case Patch(from, removed, inserted, _) =>
          target.patchInPlace(from, inserted.toSeq, removed.length)
        case Clear(_, _) =>
          target.clear()
      }
    catch {
      case NonFatal(_) =>
        resetFrom(target, source)
    }

  trait Change[V] {
    def list: ListProperty[V]
  }

  final case class Reset[V](list: ListProperty[V]) extends Change[V]
  final case class Add[V](element: V, list: ListProperty[V]) extends Change[V]
  final case class Insert[V](index: Int, element: V, list: ListProperty[V]) extends Change[V]
  final case class InsertAll[V](index: Int, elements: js.Array[V], list: ListProperty[V]) extends Change[V]
  final case class RemoveAt[V](index: Int, element: V, list: ListProperty[V]) extends Change[V]
  final case class RemoveRange[V](index: Int, elements: js.Array[V], list: ListProperty[V]) extends Change[V]
  final case class UpdateAt[V](index: Int, oldElement: V, newElement: V, list: ListProperty[V]) extends Change[V]
  final case class Patch[V](from: Int, removed: js.Array[V], inserted: js.Array[V], list: ListProperty[V]) extends Change[V]
  final case class Clear[V](removed: js.Array[V], list: ListProperty[V]) extends Change[V]

}
