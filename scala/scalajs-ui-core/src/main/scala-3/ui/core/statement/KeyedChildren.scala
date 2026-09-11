package ui.core.statement

import ui.core.component.{AbstractComponent, AbstractCustomComponent, Runtime}
import ui.core.dsl.DslLayer
import ui.core.render.{Cursor, HostMutationGuard}
import scala.collection.mutable

/** Keyed physical children. Runtime owns order/lifecycle; this index owns only keys and item data.
  * Updates do not recompose existing components. A throwing user update is not transactional;
  * callers may retry setItems with their canonical snapshot. Duplicate keys and guards are checked
  * before any mutation. Components must not be moved out behind this collection's back.
  */
final class KeyedChildren[K, V, C <: AbstractComponent](
    initial: Seq[V],
    private val key: V => K,
    create: V => C,
    update: (C, V) => Unit
) extends AbstractCustomComponent {
  private final case class Entry(component: C, value: V)
  private val entries = mutable.HashMap.empty[K, Entry]
  private var initialItems = initial.toVector
  private var updating = false

  def componentFor(id: K): Option[C] = entries.get(id).map(_.component)

  override def compose(cursor: Cursor): Unit = reconcile(initialItems, Some(cursor))

  def setItems(values: Seq[V]): Unit = {
    require(!isDisposed, "KeyedChildren is disposed.")
    if (!isBound) {
      validateKeys(values)
      initialItems = values.toVector
    } else reconcile(values.toVector, None)
  }

  private def validateKeys(values: Seq[V]): Vector[K] = {
    val keys = values.iterator.map(key).toVector
    require(keys.distinct.size == keys.size, "Duplicate child key.")
    keys
  }

  private def reconcile(values: Vector[V], initialCursor: Option[Cursor]): Unit = {
    require(!updating, "Reentrant keyed update.")
    val keys = validateKeys(values)
    if (initialCursor.isEmpty) {
      Runtime.contentCursor(this).parentHost.foreach(HostMutationGuard.checkWrite)
      children.foreach(_.physicalHosts.foreach(HostMutationGuard.checkRemoval))
    }
    updating = true
    try {
      val retained = keys.toSet
      entries.keysIterator.filterNot(retained).toVector.foreach { id =>
        Runtime.unmount(entries(id).component)
        entries.remove(id)
      }
      values.zip(keys).zipWithIndex.foreach { case ((value, id), index) =>
        entries.get(id) match {
          case Some(entry) =>
            Runtime.move(entry.component, this, index)
            if (entry.value != value) {
              update(entry.component, value)
              entries(id) = Entry(entry.component, value)
            }
          case None =>
            val component = create(value)
            require(!component.isVirtual && !component.isText,
              "Keyed children require physical element components.")
            val base = initialCursor.getOrElse(Runtime.contentCursor(this))
            val cursor = if (initialCursor.nonEmpty) base
              else children.lift(index).flatMap(_.firstPhysicalHost).map(base.before).getOrElse(base)
            Runtime.mount(component, cursor, Some(this), Some(index))
            entries(id) = Entry(component, value)
        }
      }
    } finally updating = false
  }

  /** Explicit transfer keeps both key indexes and Runtime ownership consistent. */
  def transferTo(id: K, destination: KeyedChildren[K, V, C], index: Int): Unit = {
    require(!updating && !destination.updating, "Reentrant keyed transfer.")
    require(destination ne this, "Use setItems to reorder within the same collection.")
    val entry = entries.getOrElse(id, throw new IllegalArgumentException("Unknown child key."))
    require(destination.key(entry.value) == id && !destination.entries.contains(id),
      "Destination key is different or already present.")
    Runtime.move(entry.component, destination, index)
    entries.remove(id)
    destination.entries(id) = destination.Entry(entry.component, entry.value)
  }

  override def dispose(): Unit = {
    super.dispose()
    entries.clear()
    initialItems = Vector.empty
  }
}

object KeyedChildren {
  def keyedChildren[K, V, C <: AbstractComponent](values: Seq[V])(key: V => K)(
      create: V => C
  )(update: (C, V) => Unit)(using AbstractComponent, Cursor): KeyedChildren[K, V, C] =
    DslLayer.child(new KeyedChildren(values, key, create, update)) {}
}
