package jfx.core.render

import scala.collection.mutable

final class SsrHostElement(val tagName: String) extends HostElement, SsrNode {
  private val attrs      = mutable.LinkedHashMap.empty[String, String]
  private val styles     = mutable.LinkedHashMap.empty[String, String]
  private val children   = mutable.ArrayBuffer.empty[HostNode]
  private val properties = mutable.LinkedHashMap.empty[String, Any]
  private[render] var textAreaContent: Option[TextAreaContent] = None

  def setAttribute(name: String, value: String): Unit = {
    if (attrs.get(name).contains(value)) return
    HostMutationGuard.checkWrite(this)
    attrs(name) = value
  }
  def removeAttribute(name: String): Unit = {
    if (!attrs.contains(name)) return
    HostMutationGuard.checkWrite(this)
    attrs.remove(name)
  }
  def attribute(name: String): Option[String]         = attrs.get(name)

  def setProperty(name: String, value: Any): Unit = {
    HostMutationGuard.checkRemoval(this)
    properties(name) = value
    value match {
      case boolean: Boolean if boolean => attrs(name) = name
      case _: Boolean                  => attrs.remove(name)
      case null                        => attrs.remove(name)
      case other                       => attrs(name) = other.toString
    }
  }

  def property[T](name: String): Option[T] =
    properties.get(name).asInstanceOf[Option[T]]

  def setStyle(name: String, value: String): Unit = {
    HostMutationGuard.checkWrite(this)
    styles(name) = value
  }
  def style(name: String): Option[String]         = styles.get(name)
  def removeStyle(name: String): Unit = {
    HostMutationGuard.checkWrite(this)
    styles.remove(name)
  }

  def setClassNames(names: Seq[String]): Unit =
    if (names.isEmpty) removeAttribute("class")
    else setAttribute("class", names.mkString(" "))

  // Insertion goes through SsrNode, which explains why an insertion marker's position is no longer
  // found by linear search. See CHANGE.md P4-2.
  def insertChild(index: Int, child: HostNode): Unit =
    insertBefore(child, children.lift(index))

  def insertBefore(child: HostNode, before: Option[HostNode]): Unit = {
    require(textAreaContent.isEmpty, "Textarea content does not accept child components.")
    require(before.forall {
      case node: SsrNode => node.parentElement.contains(this)
      case _ => false
    }, "Insertion anchor does not belong to this host.")
    if (before.contains(child)) return
    val ssr = child match {
      case node: SsrNode => node
      case _ => throw new IllegalArgumentException("An SSR host requires SSR children.")
    }
    var ancestor: Option[SsrHostElement] = Some(this)
    while (ancestor.nonEmpty) {
      require(!(ancestor.get eq child), "Cannot insert a host into its own subtree.")
      ancestor = ancestor.get.parentElement
    }
    HostMutationGuard.checkInsertion(this, child)
    ssr.parentElement.foreach(_.removeChild(child))
    before match {
      case Some(node) =>
        SsrNode.indexIn(children, node) match {
          case index if index >= 0 =>
            SsrNode.insertInto(children, index, child)
            // The marker moved back by exactly one position.
            SsrNode.setHint(node, index + 1)
          case _ =>
            SsrNode.appendTo(children, child)
        }

      case None =>
        SsrNode.appendTo(children, child)
    }
    ssr.parentElement = Some(this)
  }

  def removeChild(child: HostNode): Unit = {
    val index = SsrNode.indexIn(children, child)
    if (index < 0) return
    HostMutationGuard.checkWrite(this)
    HostMutationGuard.checkRemoval(child)
    children.remove(index)
    SsrNode.setHint(child, -1)
    child.asInstanceOf[SsrNode].parentElement = None
  }

  def clearChildren(): Unit = {
    HostMutationGuard.checkRemoval(this)
    children.foreach { child =>
      SsrNode.setHint(child, -1)
      child.asInstanceOf[SsrNode].parentElement = None
    }
    children.clear()
  }

  def childCount: Int = children.length

  def renderHtml(): String = {
    val styleStr =
      if (styles.isEmpty) ""
      else s""" style="${styles.map { case (k, v) => s"$k: $v" }.mkString("; ")}""""

    val attrStr = attrs.map { case (k, v) => s""" $k="${escapeAttr(v)}"""" }.mkString
    val open    = s"<$tagName$attrStr$styleStr>"

    if (VoidElements.contains(tagName)) {
      // A void element carries no children: the parser would hoist them out and the client tree
      // would afterwards hydrate against something else. Dropping them silently is the failure mode
      // ARCHITECTURE.md §7 forbids, so this reports instead.
      if (children.nonEmpty) {
        throw new IllegalStateException(
          s"<$tagName> is a void element and cannot have children, " +
            s"but ${children.length} were mounted below it."
        )
      }
      open
    } else if (textAreaContent.nonEmpty) {
      val value = textAreaContent.get.value
      // HTML parsing consumes one leading LF in textarea. An extra LF preserves the actual value.
      val prefix = if (value.startsWith("\n")) "\n" else ""
      s"$open$prefix${SsrTextNode.escape(value)}</$tagName>"
    } else {
      s"$open${renderChildrenHtml()}</$tagName>"
    }
  }

  /** The children's HTML without this element's own tags. Used for the SSR root: [[SsrCursor]]
    * roots at a nameless host so that a component reconciled away at the very top of the tree -- a
    * route outlet's loading placeholder, a root-level `Foreach` item -- is removed from the output
    * the way it would be under a real element. `renderHtml()` there would wrap everything in `<>`.
    */
  def renderChildrenHtml(): String =
    children.map(_.renderHtml()).mkString

  private def escapeAttr(value: String): String =
    value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")
}
