package jfx.core.document

import jfx.core.render.{Cursor, DomHostElement, HostElement, SsrHostElement, SsrRawTextNode, SsrTextNode}
import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.js

/** Where a [[DocumentHead]] writes what components registered.
  *
  * Two of them exist: the server writes into the `<head>` element of the SSR tree, the browser
  * reconciles the real `document.head`. Splitting them here is what lets the same registry serve
  * both without the components knowing which side they run on.
  */
trait HeadSink {
  def update(entries: Seq[HeadEntry], htmlAttributes: Seq[(String, String)]): Unit
}

object HeadSink {

  /** The attribute that marks a node in the head as belonging to a [[DocumentHead]] entry. It
    * carries the entry's key, which is how the browser sink finds a server-rendered node again.
    */
  val Marker: String = "data-jfx-head"

  /** Before a [[jfx.core.layout.Head]] connected one. A [[DocumentHead]] is usable without a head
    * element -- a component test, a render that only asks for the entries -- and then simply has
    * nowhere to write.
    */
  val Discarding: HeadSink = (_, _) => ()

  /** The sink matching `cursor`, writing into the given `<head>` and `<html>` hosts. */
  def apply(cursor: Cursor, head: HostElement, html: Option[HostElement]): HeadSink =
    (head, html) match {
      case (browserHead: DomHostElement, browserHtml) if cursor.isBrowser =>
        new BrowserHeadSink(
          browserHead.node,
          browserHtml.collect { case element: DomHostElement => element.node }
        )

      case (ssrHead: SsrHostElement, ssrHtml) =>
        new SsrHeadSink(ssrHead, ssrHtml)

      case (other, _) =>
        throw new IllegalArgumentException(
          s"No head sink for host ${other.getClass.getName} (browser=${cursor.isBrowser})."
        )
    }
}

/** Writes the head into the server-side render tree.
  *
  * The tree is mutable up to serialization, so entries a component registers late -- a route loader
  * that finishes after `<head>` was composed -- still land in the right place. That is the whole
  * reason the head is a registry rather than a component tree: in document order the head is
  * finished long before the page that describes it.
  */
final class SsrHeadSink(head: SsrHostElement, html: Option[HostElement]) extends HeadSink {

  private val ownAttributes = mutable.Set.empty[String]

  def update(entries: Seq[HeadEntry], htmlAttributes: Seq[(String, String)]): Unit = {
    head.clearChildren()
    entries.foreach(entry => head.insertChild(head.childCount, nodeFor(entry)))

    html.foreach { element =>
      val next = htmlAttributes.map(_._1).toSet
      ownAttributes.diff(next).foreach(element.removeAttribute)
      ownAttributes.clear()
      htmlAttributes.foreach { case (name, value) =>
        element.setAttribute(name, value)
        ownAttributes += name
      }
    }
  }

  private def nodeFor(entry: HeadEntry): SsrHostElement = {
    val element = new SsrHostElement(entry.tagName)

    element.setAttribute(HeadSink.Marker, entry.key)
    entry.attributes.foreach { case (name, value) => element.setAttribute(name, value) }

    entry.text.foreach { value =>
      // Not an SsrTextNode: an empty one leaves a hydration anchor behind, and head text is never
      // claimed by a component. The escaping it would apply happens here instead.
      val serialized = if (entry.rawText) value else SsrTextNode.escape(value)
      element.insertChild(0, new SsrRawTextNode(serialized))
    }

    element
  }
}

/** Reconciles `document.head` against the registered entries.
  *
  * Two rules make this survive a head that the application does not own alone -- the bundler's
  * script and stylesheet tags, whatever a Vite plugin injects in development:
  *
  *   - Nodes without the [[HeadSink.Marker]] are never touched.
  *   - A marked node is removed only once this sink has actually managed its key. A key that only
  *     ever arrived from the server -- the asset tags -- is left in place, which is what keeps
  *     hydration from tearing out the stylesheet it is running under.
  *
  * Position is not reconciled either. Nodes already in the head keep theirs, new ones are appended.
  * Order in the head carries no meaning except for `<meta charset>` and `<base>`, and those are
  * registered before the first render and therefore server-rendered in the right place.
  */
final class BrowserHeadSink(head: dom.Element, html: Option[dom.Element]) extends HeadSink {

  private val managedKeys   = mutable.Set.empty[String]
  private val ownAttributes = mutable.Set.empty[String]

  def update(entries: Seq[HeadEntry], htmlAttributes: Seq[(String, String)]): Unit = {
    val existing = markedNodes()
    val desired  = entries.map(_.key).toSet

    entries.foreach { entry =>
      managedKeys += entry.key

      val reusable =
        existing
          .get(entry.key)
          .filter(_.tagName.equalsIgnoreCase(entry.tagName))

      reusable match {
        case Some(element) =>
          sync(element, entry)

        case None =>
          existing.get(entry.key).foreach(head.removeChild)
          val element = dom.document.createElement(entry.tagName)
          sync(element, entry)
          head.appendChild(element)
      }
    }

    existing.foreach { case (key, element) =>
      if (!desired.contains(key) && managedKeys.contains(key)) head.removeChild(element)
    }

    html.foreach { element =>
      val next = htmlAttributes.map(_._1).toSet
      ownAttributes.diff(next).foreach(element.removeAttribute)
      ownAttributes.clear()
      htmlAttributes.foreach { case (name, value) =>
        if (element.getAttribute(name) != value) element.setAttribute(name, value)
        ownAttributes += name
      }
    }
  }

  private def markedNodes(): mutable.LinkedHashMap[String, dom.Element] = {
    val nodes  = head.querySelectorAll(s"[${HeadSink.Marker}]")
    val result = mutable.LinkedHashMap.empty[String, dom.Element]

    for (index <- 0 until nodes.length) {
      nodes(index) match {
        case element: dom.Element =>
          val key = element.getAttribute(HeadSink.Marker)
          if (key != null && !result.contains(key)) result(key) = element
        case _ => ()
      }
    }

    result
  }

  private def sync(element: dom.Element, entry: HeadEntry): Unit = {
    val desired = entry.attributes.toMap + (HeadSink.Marker -> entry.key)

    attributeNames(element).foreach { name =>
      if (!desired.contains(name)) element.removeAttribute(name)
    }

    desired.foreach { case (name, value) =>
      if (element.getAttribute(name) != value) element.setAttribute(name, value)
    }

    val text = entry.text.getOrElse("")
    // Assigning the same text again would be pointless work everywhere and harmful for a script:
    // the node stays, but the comparison is what keeps a re-render from rewriting it.
    if (element.textContent != text) element.textContent = text
  }

  private def attributeNames(element: dom.Element): Seq[String] =
    element
      .asInstanceOf[js.Dynamic]
      .getAttributeNames()
      .asInstanceOf[js.Array[String]]
      .toSeq
}
