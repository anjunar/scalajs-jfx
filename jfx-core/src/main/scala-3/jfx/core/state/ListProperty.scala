package jfx.core.state

import org.scalajs.dom

import scala.collection.mutable
import scala.compiletime.uninitialized
import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.util.control.NonFatal

class ListProperty[V](val underlying: js.Array[V] = js.Array[V]())
    extends ReadOnlyProperty[js.Array[V]],
      mutable.Buffer[V] {

  import ListProperty.*

  private val listeners       = mutable.ArrayBuffer.empty[js.Array[V] => Unit]
  private val changeListeners = mutable.ArrayBuffer.empty[Change[V] => Unit]
  private var disposableOwner: CompositeDisposable | Null = null
  private var defaultValue: js.Array[V]                   = underlying.slice(0, underlying.length)

  override def get: js.Array[V] =
    underlying

  def setDefaultValue(newValue: js.Array[V]): Unit = {
    defaultValue = newValue.slice(0, newValue.length)
  }

  def isDirty: Boolean = !arrayEquals(underlying, defaultValue)

  private def arrayEquals[A](left: js.Array[A], right: js.Array[A]): Boolean =
    (left eq right) ||
      left.length == right.length &&
      left.indices.forall(index => left(index) == right(index))

  def registerDisposableOwner(owner: CompositeDisposable): this.type = {
    disposableOwner = owner
    this
  }

  private[state] def autoRegister(disposable: Disposable): Unit =
    if (disposableOwner != null) {
      disposableOwner.add(disposable)
    }

  private[state] def hasSameDisposableOwnerAs(other: ListProperty[?]): Boolean =
    disposableOwner != null && disposableOwner.eq(other.disposableOwner)

  def notified(): Unit =
    notified(Reset(this))

  def notified(change: Change[V]): Unit = {
    changeListeners.toVector.foreach(_(change))
    listeners.toVector.foreach(_(get))
  }

  override def observe(listener: js.Array[V] => Unit): Disposable = {
    listeners += listener
    listener(get)
    () => listeners -= listener
  }

  override def observeWithoutInitial(listener: js.Array[V] => Unit): Disposable = {
    listeners += listener
    () => listeners -= listener
  }

  def observeChanges(listener: Change[V] => Unit): Disposable = {
    changeListeners += listener
    () => changeListeners -= listener
  }

  override def prepend(elem: V): this.type = {
    insert(0, elem)
    this
  }

  override def insert(idx: Int, elem: V): Unit = {
    if (idx < 0 || idx > underlying.length) throw IndexOutOfBoundsException(s"$idx")
    underlying.splice(idx, 0, elem)
    notified(Insert(idx, elem, this))
  }

  override def insertAll(idx: Int, elems: IterableOnce[V]): Unit = {
    if (idx < 0 || idx > underlying.length) throw IndexOutOfBoundsException(s"$idx")
    val seq = elems.iterator.toSeq
    if (seq.isEmpty) return

    val inserted = js.Array(seq*)
    underlying.splice(idx, 0, seq*)
    notified(InsertAll(idx, inserted, this))
  }

  override def remove(idx: Int): V = {
    if (idx < 0 || idx >= underlying.length) throw IndexOutOfBoundsException(s"$idx")
    val removed = underlying.splice(idx, 1)
    val element = removed(0)
    notified(RemoveAt(idx, element, this))
    element
  }

  override def remove(idx: Int, count: Int): Unit = {
    if (count < 0) throw IllegalArgumentException(s"$count")
    if (idx < 0 || idx > underlying.length) throw IndexOutOfBoundsException(s"$idx")
    if (idx + count > underlying.length) throw IndexOutOfBoundsException(s"${idx + count}")
    if (count == 0) return

    val removed = underlying.splice(idx, count)
    notified(RemoveRange(idx, removed, this))
  }

  override def patchInPlace(from: Int, patch: IterableOnce[V], replaced: Int): this.type = {
    if (replaced < 0) throw IllegalArgumentException(s"$replaced")
    if (from < 0 || from > underlying.length) throw IndexOutOfBoundsException(s"$from")
    if (from + replaced > underlying.length) throw IndexOutOfBoundsException(s"${from + replaced}")

    val seq = patch.iterator.toSeq
    if (seq.isEmpty && replaced == 0) return this

    val inserted = js.Array(seq*)
    val removed  = underlying.splice(from, replaced, seq*)
    notified(Patch(from, removed, inserted, this))
    this
  }

  override def addOne(elem: V): this.type = {
    underlying.push(elem)
    notified(Add(elem, this))
    this
  }

  def setAll(elems: IterableOnce[V]): this.type = {
    val seq = elems.iterator.toSeq
    if (underlying.length == 0 && seq.isEmpty) return this

    underlying.splice(0, underlying.length, seq*)
    notified(Reset(this))
    this
  }

  override def clear(): Unit = {
    if (underlying.length == 0) return

    val removed = underlying.splice(0, underlying.length)
    notified(Clear(removed, this))
  }

  override def update(idx: Int, elem: V): Unit = {
    if (idx < 0 || idx >= underlying.length) throw IndexOutOfBoundsException(s"$idx")
    val oldElement = underlying(idx)
    if (oldElement == elem) return

    underlying(idx) = elem
    notified(UpdateAt(idx, oldElement, elem, this))
  }

  override def apply(i: Int): V = {
    if (i < 0 || i >= underlying.length) throw IndexOutOfBoundsException(s"$i")
    underlying(i)
  }

  override def length: Int =
    underlying.length

  override def iterator: Iterator[V] =
    underlying.iterator

  def totalLength: Int =
    length


  def asProperty: ReadOnlyProperty[js.Array[V]] = this

}

object ListProperty {

  def apply[V](underlying: js.Array[V] = js.Array[V]()): ListProperty[V] =
    new ListProperty[V](underlying)

  def owned[V](
      owner: CompositeDisposable,
      underlying: js.Array[V] = js.Array[V]()
  ): ListProperty[V] =
    new ListProperty[V](underlying).registerDisposableOwner(owner)


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
    a.autoRegister(composite)
    if ((b ne a) && !a.hasSameDisposableOwnerAs(b)) {
      b.autoRegister(composite)
    }
    composite
  }

  private def resetFrom[V](target: ListProperty[V], source: ListProperty[V]): Unit =
    target.setAll(source.get.toSeq)

  private def applyChange[V](
      source: ListProperty[V],
      target: ListProperty[V],
      change: Change[V]
  ): Unit =
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

  trait Change[V] {
    def list: ListProperty[V]
  }

  final case class Reset[V](list: ListProperty[V])                          extends Change[V]
  final case class Add[V](element: V, list: ListProperty[V])                extends Change[V]
  final case class Insert[V](index: Int, element: V, list: ListProperty[V]) extends Change[V]
  final case class InsertAll[V](index: Int, elements: js.Array[V], list: ListProperty[V])
      extends Change[V]
  final case class RemoveAt[V](index: Int, element: V, list: ListProperty[V]) extends Change[V]
  final case class RemoveRange[V](index: Int, elements: js.Array[V], list: ListProperty[V])
      extends Change[V]
  final case class UpdateAt[V](index: Int, oldElement: V, newElement: V, list: ListProperty[V])
      extends Change[V]
  final case class Patch[V](
      from: Int,
      removed: js.Array[V],
      inserted: js.Array[V],
      list: ListProperty[V]
  ) extends Change[V]
  final case class Clear[V](removed: js.Array[V], list: ListProperty[V]) extends Change[V]

}
