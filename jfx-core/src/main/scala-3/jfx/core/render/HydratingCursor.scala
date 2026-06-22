package jfx.core.render

import org.scalajs.dom

final class HydratingCursor private (
  parent: dom.Node,
  private var nextNode: Option[dom.Node],
  stopBefore: Option[dom.Node],
  mode : HydrationMode = HydrationMode.Strict
) extends Cursor {

  override def supportsAnchors: Boolean = true

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
        throw new IllegalStateException(
          s"Hydration erwartet <$tag>, gefunden wurde <${element.tagName.toLowerCase}>."
        )

      case other =>
        throw new IllegalStateException(
          s"Hydration erwartet <$tag>, gefunden wurde ${other.nodeName}."
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

    throw new IllegalStateException(
      s"Hydration erwartet im <head> <$tag>, konnte aber kein passendes Element finden."
    )
  }

  def claimText(initial: String): TextNode = {
    val node = take()
    node match {
      case text: dom.Text => new DomTextNode(text)
      case other => throw new IllegalStateException(s"Hydration erwartet TextNode, gefunden wurde ${other.nodeName}.")
    }
  }

  override def claimComment(text: String): CommentNode = {
    val node = take()
    node match {
      case comment: dom.Comment => new DomCommentNode(comment)
      case other => throw new IllegalStateException(s"Hydration erwartet CommentNode, gefunden wurde ${other.nodeName}.")
    }
  }

  override def claimRange(label: String): VirtualRange = {
    val startNode = takeComment(s"jfx:$label:start")
    val endNode = findEnd(startNode, s"jfx:$label:end")
    nextNode = Option(endNode.nextSibling)
    val start = new DomCommentNode(startNode)
    val end = new DomCommentNode(endNode)
    val inner = new HydratingCursor(parent, Option(startNode.nextSibling).filter(_ != endNode), Some(endNode))
    VirtualRange(start, end, inner)
  }

  def sub(host: HostElement): Cursor = {
    val raw = DomNodes.raw(host)

    val mode =
      raw match {
        case e: dom.Element if e.tagName.equalsIgnoreCase("head") =>
          HydrationMode.Head
        case _ =>
          HydrationMode.Strict
      }

    new HydratingCursor(raw, Option(raw.firstChild), None, mode)
  }

  override def before(node: HostNode): Cursor =
    DomCursor.before(parent, DomNodes.raw(node))

  private def take(): dom.Node =
    nextNode match {
      case Some(node) if stopBefore.contains(node) =>
        throw new IllegalStateException("Hydration hat das Ende der aktuellen virtuellen Range erreicht.")
      case Some(node) =>
        nextNode = Option(node.nextSibling).filter(next => !stopBefore.contains(next))
        node
      case None =>
        throw new IllegalStateException("Hydration erwartet eine weitere DOM-Node, aber es gibt keine mehr.")
    }

  private def takeComment(expected: String): dom.Comment = {
    val node = take()
    node match {
      case comment: dom.Comment if comment.data == expected =>
        comment
      case comment: dom.Comment =>
        throw new IllegalStateException(s"Hydration erwartet Kommentar '$expected', gefunden wurde '${comment.data}'.")
      case other =>
        throw new IllegalStateException(s"Hydration erwartet Kommentar '$expected', gefunden wurde ${other.nodeName}.")
    }
  }

  private def findEnd(start: dom.Comment, expected: String): dom.Comment = {
    var current = start.nextSibling
    var depth = 0
    while (current != null) {
      current match {
        case comment: dom.Comment if comment.data.endsWith(":start") =>
          depth += 1
        case comment: dom.Comment if comment.data == expected && depth == 0 =>
          return comment
        case comment: dom.Comment if comment.data.endsWith(":end") && depth > 0 =>
          depth -= 1
        case _ => ()
      }
      current = current.nextSibling
    }
    throw new IllegalStateException(s"Hydration konnte den End-Anker '$expected' nicht finden.")
  }
}

object HydratingCursor {

  def root(container: dom.Element): HydratingCursor =
    new HydratingCursor(
      parent = container,
      nextNode = firstHydratableChild(container),
      stopBefore = None
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
