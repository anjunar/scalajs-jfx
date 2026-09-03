package jfx.core.document

import jfx.core.component.AbstractComponent
import jfx.core.di.Context
import jfx.core.state.Disposable

import scala.collection.mutable

/** What the document head contains, collected from the whole component tree.
  *
  * The head is composed at the top of the document, but the components that know what belongs in it
  * -- the page, the article, the route -- come far below and often only after a loader returned. So
  * the head is not a component tree but a registry: components register entries, and a [[HeadSink]]
  * writes them out. On the server that sink writes into the still-mutable SSR tree, in the browser
  * it reconciles `document.head`.
  *
  * Registrations stack per [[HeadEntry.key]]: the last one wins, and disposing it uncovers the one
  * below. That is what lets a page override the site-wide description and restores the default when
  * the page goes away, without either side knowing about the other.
  *
  * One instance per request. It is instance state on the component tree, never an `object` --
  * ARCHITECTURE.md §5.
  */
final class DocumentHead {

  private final class Registration(val key: String, val entry: HeadEntry)

  private val stacks = mutable.HashMap.empty[String, mutable.ArrayBuffer[Registration]]

  /** Position of a key in the head, assigned when it first appears and kept afterwards -- also
    * while nothing is registered under it. Without that, a handle that re-registers the same key on
    * every navigation would move its element to the end of the head each time.
    */
  private val order = mutable.LinkedHashMap.empty[String, Int]

  private val attributes = mutable.LinkedHashMap.empty[String, String]

  private var sink       = HeadSink.Discarding
  private var batchDepth = 0
  private var dirty      = false

  /** Registers `entry`; disposing the result removes it again. */
  def push(entry: HeadEntry): Disposable = {
    val registration = new Registration(entry.key, entry)

    stacks.getOrElseUpdate(entry.key, mutable.ArrayBuffer.empty) += registration
    if (!order.contains(entry.key)) {
      order(entry.key) = order.size
    }
    flush()

    Disposable {
      stacks.get(registration.key).foreach { stack =>
        val index = stack.indexWhere(_ eq registration)
        if (index >= 0) stack.remove(index)
        if (stack.isEmpty) stacks.remove(registration.key)
      }
      flush()
    }
  }

  /** Registers entries for as long as `component` lives. */
  def bind(entries: HeadEntry*)(using component: AbstractComponent): Unit =
    batch {
      entries.foreach(entry => component.addDisposable(push(entry)))
    }

  /** An attribute on `<html>`, `lang` and `dir` above all. Last write wins; there is one document
    * element, and no component owns it the way a page owns its description.
    */
  def htmlAttribute(name: String, value: String): Unit = {
    if (!attributes.get(name).contains(value)) {
      attributes(name) = value
      flush()
    }
  }

  def removeHtmlAttribute(name: String): Unit =
    if (attributes.remove(name).isDefined) flush()

  /** A group of entries that is replaced as a whole.
    *
    * For everything that changes without its component going away -- the title after a navigation,
    * the canonical URL after a locale switch. Registering again without dropping the previous
    * registration would otherwise pile up the stack.
    */
  def handle(): DocumentHead.Handle =
    new DocumentHead.Handle(this)

  /** A handle that goes away with `owner`. */
  def handle(owner: AbstractComponent): DocumentHead.Handle = {
    val created = new DocumentHead.Handle(this)
    owner.addDisposable(created)
    created
  }

  /** The entries as the head should contain them: one per key, in the order the keys first
    * appeared.
    */
  def entries: Seq[HeadEntry] =
    order.toSeq
      .sortBy(_._2)
      .flatMap { case (key, _) => stacks.get(key).flatMap(_.lastOption).map(_.entry) }

  def htmlAttributes: Seq[(String, String)] =
    attributes.toSeq

  /** Groups changes into a single write to the sink. */
  def batch[A](body: => A): A = {
    batchDepth += 1
    try body
    finally {
      batchDepth -= 1
      if (batchDepth == 0 && dirty) {
        dirty = false
        sink.update(entries, htmlAttributes)
      }
    }
  }

  /** Called by [[jfx.core.layout.Head]] once it knows its host element. Everything registered
    * before that point is written out immediately afterwards.
    */
  private[jfx] def connect(next: HeadSink): Unit = {
    sink = next
    dirty = true
    if (batchDepth == 0) {
      dirty = false
      sink.update(entries, htmlAttributes)
    }
  }

  private def flush(): Unit =
    if (batchDepth > 0) dirty = true
    else sink.update(entries, htmlAttributes)
}

object DocumentHead {

  val DocumentHeadContext: Context[DocumentHead] =
    Context.create[DocumentHead]("DocumentHead")

  def provide(head: DocumentHead)(using AbstractComponent): Unit =
    DocumentHeadContext.provide(head)

  def current(using AbstractComponent): Option[DocumentHead] =
    DocumentHeadContext.inject

  def requireCurrent(using AbstractComponent): DocumentHead =
    current.getOrElse {
      throw new IllegalStateException("No DocumentHead found in the current component tree.")
    }

  /** Entries of one owner, replaced as a whole on every [[set]]. */
  final class Handle private[document] (head: DocumentHead) extends Disposable {

    private var registrations = Seq.empty[Disposable]

    def set(entries: HeadEntry*): Unit =
      head.batch {
        registrations.foreach(_.dispose())
        registrations = entries.map(head.push)
      }

    def clear(): Unit = set()

    def dispose(): Unit = {
      head.batch {
        registrations.foreach(_.dispose())
        registrations = Seq.empty
      }
    }
  }
}
