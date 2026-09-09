package jfx.core.render

import jfx.core.async.AsyncRenderContext
import org.scalajs.dom
import jfx.core.state.Disposable

final class HydratingCursor private (
    parent: dom.Node,
    private var nextNode: Option[dom.Node],
    stopBefore: Option[dom.Node],
    mode: HydrationMode = HydrationMode.Strict,
    currentAsyncContext: Option[AsyncRenderContext] = None,
    session: HydratingCursor.HydrationSession
) extends Cursor {

  session.register(this)

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

  override def parentHost: Option[HostElement] =
    parent match {
      case element: dom.Element => Some(new DomHostElement(element))
      case _                    => None
    }

  override def completeHydration(): Unit =
    session.complete()

  override def afterHydration(callback: () => Unit): Unit =
    session.afterComplete(callback)

  override def insertion: Cursor =
    new DeferredHydrationCursor(DomCursor.append(parent, stopBefore, currentAsyncContext),
      callback => session.afterComplete(callback))

  override def withHydrationBoundary(body: Cursor => Unit): Disposable = {
    val local = new HydratingCursor.HydrationSession(Some(session))
    val cursor = new HydratingCursor(parent, nextNode, stopBefore, mode, currentAsyncContext, local)
    // This scope owns the complete remaining range, on success and on recovery.
    nextNode = stopBefore
    try {
      body(cursor)
      local.complete()
      Disposable(local.cancel())
    } catch {
      case error: Throwable =>
        local.cancel()
        throw error
    }
  }

  override def claimTextAreaContent(initial: String): TextAreaContent = {
    val host = parentHost.getOrElse(throw new IllegalStateException("Missing textarea host."))
    val content = TextAreaContent.attach(host, initial, true)
    // Textarea RCDATA is owned by the value adapter, not individual TextComponents.
    nextNode = stopBefore
    content
  }

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
          "Element tag does not match.",
          expected = s"<$tag>",
          found = Some(element)
        )

      case other =>
        throw hydrationFault(
          "DOM node type does not match.",
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
      "No matching element was found in <head>.",
      expected = s"<$tag>",
      found = nextNode
    )
  }

  def claimText(initial: String): TextNode = {
    val node = take()
    node match {
      case text: dom.Text => new DomTextNode(text)

      // An empty text node left an anchor comment behind during SSR, because an empty text
      // serializes to nothing at all. Swap it for the text node the client tree expects.
      case comment: dom.Comment if comment.data == SsrTextNode.EmptyAnchorLabel =>
        val text = parent.ownerDocument.createTextNode("")
        HostMutationGuard.checkDom(parent, destructive = false)
        HostMutationGuard.checkDom(comment, destructive = true)
        comment.parentNode.replaceChild(text, comment)
        new DomTextNode(text)

      case other =>
        throw hydrationFault(
          "DOM node type does not match.",
          expected = "TextNode",
          found = Some(other)
        )
    }
  }

  override def claimComment(text: String): CommentNode = {
    val node = take()
    node match {
      case comment: dom.Comment => new DomCommentNode(comment)
      case other                =>
        throw hydrationFault(
          "DOM node type does not match.",
          expected = "CommentNode",
          found = Some(other)
        )
    }
  }

  override def claimRange(label: String): VirtualRange =
    rangeFor(label, adopt = false)

  /** Adopts the range without validating its contents.
    *
    * The nodes between the anchors belong to the component so that they disappear when it is
    * replaced. See [[HydrationMode.Adopt]].
    */
  override def adoptRange(label: String): VirtualRange =
    rangeFor(label, adopt = true)

  private def rangeFor(label: String, adopt: Boolean): VirtualRange = {
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
        mode = if (adopt) HydrationMode.Adopt else HydrationMode.Strict,
        currentAsyncContext = currentAsyncContext,
        session = session
      )

    val adoptedNodes =
      if (!adopt) Nil
      else {
        val buffer  = scala.collection.mutable.ArrayBuffer.empty[HostNode]
        var current = startNode.nextSibling
        while (current != null && current != endNode) {
          buffer += DomNodes.wrap(current)
          current = current.nextSibling
        }
        buffer.toSeq
      }

    VirtualRange(start, end, inner, adoptedNodes)
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
      currentAsyncContext = currentAsyncContext,
      session = session
    )
  }

  override def fresh: Cursor =
    if (!session.isCompleted) this
    else insertion

  override def before(node: HostNode): Cursor =
    insertion.before(node)

  private def take(): dom.Node =
    nextNode match {
      case Some(node) if stopBefore.contains(node) =>
        throw hydrationFault(
          "The end of the current virtual range has been reached.",
          expected = "another DOM node before the range end anchor",
          found = Some(node)
        )

      case Some(node) =>
        nextNode = Option(node.nextSibling).filter(next => !stopBefore.contains(next))
        node

      case None =>
        throw hydrationFault(
          "There is no further DOM node.",
          expected = "another DOM node",
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
          "Comment anchor does not match.",
          expected = s"<!--$expected-->",
          found = Some(comment)
        )

      case other =>
        throw hydrationFault(
          "DOM node type does not match.",
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
      "End anchor was not found.",
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
         |Expected: $expected
         |Found: ${found.map(describeNode).getOrElse("<no further DOM node>")}
         |Parent: ${describePath(parent)}
         |Context:
         |${describeContext(found)}
         |
         |Note: the SSR HTML and the client component tree differ at this position.""".stripMargin
    )

  private[render] def assertFullyClaimed(): Unit =
    // Adopt: the range was deliberately adopted without validation, so "unclaimed" is normal there
    // rather than an error.
    if (mode == HydrationMode.Strict) {
      firstUnclaimedNode.foreach { node =>
        throw hydrationFault(
          "Server-rendered nodes were not claimed by the client component tree.",
          expected = "end of the current hydration range",
          found = Some(node)
        )
      }
    }

  private def firstUnclaimedNode: Option[dom.Node] = {
    var current = nextNode

    while (current.nonEmpty && HydratingCursor.isIgnorableWhitespace(current.get)) {
      current = Option(current.get.nextSibling).filter(next => !stopBefore.contains(next))
    }

    current.filter(node => !stopBefore.contains(node))
  }

  private def describeContext(focus: Option[dom.Node]): String = {
    val contextParent = focus.flatMap(node => Option(node.parentNode)).getOrElse(parent)
    val children      = contextParent.childNodes
    val focusIndex    = focus.map(indexOfChild(contextParent, _)).getOrElse(-1)
    val start         =
      if (focusIndex >= 0) math.max(0, focusIndex - 2)
      else math.max(0, children.length - 5)
    val end =
      if (focusIndex >= 0) math.min(children.length, focusIndex + 3)
      else children.length

    if (children.length == 0)
      "  <no child nodes>"
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

      if (lines.isEmpty) "  <no child nodes>" else lines.toString()
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

  private final class HydrationSession(enclosing: Option[HydrationSession] = None) {
    private val cursors   = scala.collection.mutable.ArrayBuffer.empty[HydratingCursor]
    private val callbacks = scala.collection.mutable.ArrayBuffer.empty[() => Unit]
    private var completed = false
    private var activated = false
    private var cancelled = false

    def isCompleted: Boolean = completed

    def register(cursor: HydratingCursor): Unit =
      if (completed || cancelled) {
        throw new IllegalStateException(
          "Another HydratingCursor was created after hydration completed."
        )
      } else {
        cursors += cursor
      }

    def afterComplete(callback: () => Unit): Unit =
      if (cancelled) ()
      else if (activated) callback()
      else callbacks += callback

    def cancel(): Unit = {
      cancelled = true
      callbacks.clear()
      cursors.clear()
    }

    private def activate(): Unit =
      if (!cancelled) {
        activated = true
        val pending = callbacks.toVector
        callbacks.clear()
        pending.foreach(callback => if (!cancelled) callback())
      }

    def complete(): Unit =
      if (!completed) {
        cursors.toVector.foreach(_.assertFullyClaimed())
        completed = true
        cursors.clear()
        enclosing match {
          case Some(parent) => parent.afterComplete(() => activate())
          case None => activate()
        }
      }
  }

  def root(container: dom.Element): HydratingCursor = {
    val session = new HydrationSession()
    new HydratingCursor(
      parent = container,
      nextNode = firstHydratableChild(container),
      stopBefore = None,
      session = session
    )
  }

  def root(container: dom.Element, asyncContext: AsyncRenderContext): HydratingCursor = {
    val session = new HydrationSession()
    new HydratingCursor(
      parent = container,
      nextNode = firstHydratableChild(container),
      stopBefore = None,
      currentAsyncContext = Some(asyncContext),
      session = session
    )
  }

  /** Hydration of the whole document.
    *
    * The cursor sits above `<html>`, so the root component claims that element and its children
    * claim `<head>` and `<body>`. Everything the document carries besides the document element --
    * the doctype, comments before or after it -- belongs to no component and is skipped: the
    * component tree starts at `<html>`.
    */
  def root(document: dom.Document, asyncContext: AsyncRenderContext): HydratingCursor = {
    val session = new HydrationSession()
    new HydratingCursor(
      parent = document,
      nextNode = Option(document.documentElement),
      stopBefore = None,
      currentAsyncContext = Some(asyncContext),
      session = session
    )
  }

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
