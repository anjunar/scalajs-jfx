package jfx.core.state

import org.scalajs.dom

import scala.collection.mutable
import scala.compiletime.uninitialized
import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.util.control.NonFatal

class ListProperty[V](initialValue: js.Array[V] = js.Array[V]())
    extends ReadOnlyProperty[js.Array[V]],
      mutable.Buffer[V],
      ListDataSource[V] {

  import ListProperty.*

  private val underlying      = initialValue.slice(0, initialValue.length)
  private val listeners       = mutable.ArrayBuffer.empty[js.Array[V] => Unit]
  private val changeListeners = mutable.ArrayBuffer.empty[Change[V] => Unit]
  private var disposableOwner: CompositeDisposable | Null = null
  private var defaultValue: js.Array[V]                   = underlying.slice(0, underlying.length)

  override def get: js.Array[V] =
    underlying.slice(0, underlying.length)

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

  override def observeChanges(listener: Change[V] => Unit): Disposable = {
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

  override def totalLength: Int =
    length

  override def itemAt(index: Int): Option[V] =
    Option.when(index >= 0 && index < length)(underlying(index))

  def asProperty: ReadOnlyProperty[js.Array[V]] = this

}

object ListProperty {

  type Change[V] = ListDataSource.Change[V]
  val Reset: ListDataSource.Reset.type             = ListDataSource.Reset
  val Add: ListDataSource.Add.type                 = ListDataSource.Add
  val Insert: ListDataSource.Insert.type           = ListDataSource.Insert
  val InsertAll: ListDataSource.InsertAll.type     = ListDataSource.InsertAll
  val RemoveAt: ListDataSource.RemoveAt.type       = ListDataSource.RemoveAt
  val RemoveRange: ListDataSource.RemoveRange.type = ListDataSource.RemoveRange
  val UpdateAt: ListDataSource.UpdateAt.type       = ListDataSource.UpdateAt
  val Patch: ListDataSource.Patch.type             = ListDataSource.Patch
  val Clear: ListDataSource.Clear.type             = ListDataSource.Clear

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

}
