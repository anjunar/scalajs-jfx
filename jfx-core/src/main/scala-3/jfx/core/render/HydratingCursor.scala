package jfx.core.render

import jfx.core.async.AsyncRenderContext
import org.scalajs.dom

final class HydratingCursor private (
    parent: dom.Node,
    private var nextNode: Option[dom.Node],
    stopBefore: Option[dom.Node],
    mode: HydrationMode = HydrationMode.Strict,
    currentAsyncContext: Option[AsyncRenderContext] = None
) extends Cursor {

  override def supportsAnchors: Boolean =
    true

  override def isBrowser: Boolean =
    true

  override def isHydrating: Boolean =
    true

  override def browserUrl: Option[String] =
    Some(s"${dom.window.location.pathname}${dom.window.location.search}")

  override def asyncContext: Option[AsyncRenderContext] =
    currentAsyncContext

  def claimElement(tag: String): HostElement = {
    val node =
      if (mode == HydrationMode.Head)
        takeExpectedElementInHead(tag)
      else
        take()

    node match {
      case element: dom.Element if element.tagName.equalsIgnoreCase(tag) =>
        new DomHostElement(element)

      case element: dom.Element =>
        throw hydrationFault(
          "Element-Tag stimmt nicht.",
          expected = s"<$tag>",
          found = Some(element)
        )

      case other =>
        throw hydrationFault(
          "DOM-Node-Typ stimmt nicht.",
          expected = s"<$tag>",
          found = Some(other)
        )
    }
  }

  private def takeExpectedElementInHead(tag: String): dom.Node = {
    var current = nextNode

    while (current.nonEmpty && !stopBefore.contains(current.get)) {
      current.get match {
        case element: dom.Element if element.tagName.equalsIgnoreCase(tag) =>
          nextNode = Option(element.nextSibling).filter(next => !stopBefore.contains(next))
          return element

        case _ =>
          current = Option(current.get.nextSibling)
      }
    }

    throw hydrationFault(
      "Im <head> wurde kein passendes Element gefunden.",
      expected = s"<$tag>",
      found = nextNode
    )
  }

  def claimText(initial: String): TextNode = {
    val node = take()
    node match {
      case text: dom.Text => new DomTextNode(text)
      case other =>
        throw hydrationFault(
          "DOM-Node-Typ stimmt nicht.",
          expected = "TextNode",
          found = Some(other)
        )
    }
  }

  override def claimComment(text: String): CommentNode = {
    val node = take()
    node match {
      case comment: dom.Comment => new DomCommentNode(comment)
      case other =>
        throw hydrationFault(
          "DOM-Node-Typ stimmt nicht.",
          expected = "CommentNode",
          found = Some(other)
        )
    }
  }

  override def claimRange(label: String): VirtualRange = {
    val startNode = takeComment(s"jfx:$label:start")
    val endNode   = findEnd(startNode, s"jfx:$label:end")

    nextNode = Option(endNode.nextSibling)

    val start = new DomCommentNode(startNode)
    val end   = new DomCommentNode(endNode)

    val inner =
      new HydratingCursor(
        parent = parent,
        nextNode = Option(startNode.nextSibling).filter(_ != endNode),
        stopBefore = Some(endNode),
        mode = HydrationMode.Strict,
        currentAsyncContext = currentAsyncContext
      )

    VirtualRange(start, end, inner)
  }

  def sub(host: HostElement): Cursor = {
    val raw = DomNodes.raw(host)

    val nextMode =
      raw match {
        case e: dom.Element if e.tagName.equalsIgnoreCase("head") =>
          HydrationMode.Head
        case _ =>
          HydrationMode.Strict
      }

    new HydratingCursor(
      parent = raw,
      nextNode = Option(raw.firstChild),
      stopBefore = None,
      mode = nextMode,
      currentAsyncContext = currentAsyncContext
    )
  }

  override def before(node: HostNode): Cursor =
    DomCursor.before(parent, DomNodes.raw(node), currentAsyncContext)

  private def take(): dom.Node =
    nextNode match {
      case Some(node) if stopBefore.contains(node) =>
        throw hydrationFault(
          "Das Ende der aktuellen virtuellen Range wurde erreicht.",
          expected = "weitere DOM-Node vor dem Range-End-Anker",
          found = Some(node)
        )

      case Some(node) =>
        nextNode = Option(node.nextSibling).filter(next => !stopBefore.contains(next))
        node

      case None =>
        throw hydrationFault(
          "Es gibt keine weitere DOM-Node.",
          expected = "weitere DOM-Node",
          found = None
        )
    }

  private def takeComment(expected: String): dom.Comment = {
    val node = take()
    node match {
      case comment: dom.Comment if comment.data == expected =>
        comment

      case comment: dom.Comment =>
        throw hydrationFault(
          "Kommentar-Anker stimmt nicht.",
          expected = s"<!--$expected-->",
          found = Some(comment)
        )

      case other =>
        throw hydrationFault(
          "DOM-Node-Typ stimmt nicht.",
          expected = s"<!--$expected-->",
          found = Some(other)
        )
    }
  }

  private def findEnd(start: dom.Comment, expected: String): dom.Comment = {
    var current = start.nextSibling
    var depth   = 0

    while (current != null) {
      current match {
        case comment: dom.Comment if comment.data.endsWith(":start") =>
          depth += 1

        case comment: dom.Comment if comment.data == expected && depth == 0 =>
          return comment

        case comment: dom.Comment if comment.data.endsWith(":end") && depth > 0 =>
          depth -= 1

        case _ =>
          ()
      }

      current = current.nextSibling
    }

    throw hydrationFault(
      "End-Anker wurde nicht gefunden.",
      expected = s"<!--$expected-->",
      found = Option(start.nextSibling)
    )
  }

  private def hydrationFault(
      reason: String,
      expected: String,
      found: Option[dom.Node]
  ): IllegalStateException =
    new IllegalStateException(
      s"""Hydration fault: $reason
         |Erwartet: $expected
         |Gefunden: ${found.map(describeNode).getOrElse("<keine weitere DOM-Node>")}
         |Parent: ${describePath(parent)}
         |Umgebung:
         |${describeContext(found)}
         |
         |Hinweis: SSR-HTML und Client-Komponentenbaum unterscheiden sich an dieser Position.""".stripMargin
    )

  private def describeContext(focus: Option[dom.Node]): String = {
    val contextParent = focus.flatMap(node => Option(node.parentNode)).getOrElse(parent)
    val children      = contextParent.childNodes
    val focusIndex    = focus.map(indexOfChild(contextParent, _)).getOrElse(-1)
    val start =
      if (focusIndex >= 0) math.max(0, focusIndex - 2)
      else math.max(0, children.length - 5)
    val end =
      if (focusIndex >= 0) math.min(children.length, focusIndex + 3)
      else children.length

    if (children.length == 0)
      "  <keine Child-Nodes>"
    else {
      val lines = new StringBuilder
      var index = start

      while (index < end) {
        val node   = children.item(index)
        val marker = if (focus.contains(node)) ">" else " "
        lines.append(s"  $marker [$index] ${describeNode(node)}")
        if (index < end - 1) lines.append('\n')
        index += 1
      }

      if (lines.isEmpty) "  <keine Child-Nodes>" else lines.toString()
    }
  }

  private def indexOfChild(parent: dom.Node, child: dom.Node): Int = {
    val children = parent.childNodes
    var index    = 0

    while (index < children.length) {
      if (children.item(index) == child) return index
      index += 1
    }

    -1
  }

  private def describePath(node: dom.Node): String = {
    val parts   = scala.collection.mutable.ArrayBuffer.empty[String]
    var current = Option(node)

    while (current.nonEmpty && current.get.nodeType != dom.Node.DOCUMENT_NODE) {
      parts.insert(0, describePathPart(current.get))
      current = Option(current.get.parentNode)
    }

    if (parts.isEmpty) describeNode(node) else parts.mkString(" > ")
  }

  private def describePathPart(node: dom.Node): String =
    node match {
      case element: dom.Element =>
        val idPart =
          Option(element.getAttribute("id")).filter(_.nonEmpty).map(id => s"#$id").getOrElse("")
        val classPart =
          Option(element.getAttribute("class"))
            .map(_.trim)
            .filter(_.nonEmpty)
            .map(_.split("\\s+").take(3).mkString(".", ".", ""))
            .getOrElse("")

        s"${element.tagName.toLowerCase}$idPart$classPart"

      case _ =>
        node.nodeName
    }

  private def describeNode(node: dom.Node): String =
    node match {
      case element: dom.Element =>
        val tag       = element.tagName.toLowerCase
        val idPart    = attributePart(element, "id")
        val classPart = attributePart(element, "class")
        s"<$tag$idPart$classPart>"

      case text: dom.Text =>
        s"""TextNode("${clip(text.data)}")"""

      case comment: dom.Comment =>
        s"<!--${clip(comment.data)}-->"

      case _ =>
        node.nodeName
    }

  private def attributePart(element: dom.Element, name: String): String =
    Option(element.getAttribute(name))
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(value => s""" $name="${clip(value)}"""")
      .getOrElse("")

  private def clip(value: String, maxLength: Int = 120): String = {
    val normalized = value.replaceAll("\\s+", " ").trim
    if (normalized.length <= maxLength) normalized
    else normalized.take(maxLength - 1) + "…"
  }
}

object HydratingCursor {

  def root(container: dom.Element): HydratingCursor =
    new HydratingCursor(
      parent = container,
      nextNode = firstHydratableChild(container),
      stopBefore = None
    )

  def root(container: dom.Element, asyncContext: AsyncRenderContext): HydratingCursor =
    new HydratingCursor(
      parent = container,
      nextNode = firstHydratableChild(container),
      stopBefore = None,
      currentAsyncContext = Some(asyncContext)
    )

  private def firstHydratableChild(parent: dom.Node): Option[dom.Node] = {
    var current = parent.firstChild

    while (current != null && isIgnorableWhitespace(current)) {
      current = current.nextSibling
    }

    Option(current)
  }

  private def isIgnorableWhitespace(node: dom.Node): Boolean =
    node.nodeType == dom.Node.TEXT_NODE && node.textContent.trim.isEmpty
}
