package jfx.core.render

import scala.collection.mutable

final class SsrHostElement(val tagName: String) extends HostElement, SsrNode {
  private val attrs      = mutable.LinkedHashMap.empty[String, String]
  private val styles     = mutable.LinkedHashMap.empty[String, String]
  private val children   = mutable.ArrayBuffer.empty[HostNode]
  private val properties = mutable.LinkedHashMap.empty[String, Any]

  def setAttribute(name: String, value: String): Unit = attrs(name) = value
  def removeAttribute(name: String): Unit             = attrs.remove(name)
  def attribute(name: String): Option[String]         = attrs.get(name)

  def setProperty(name: String, value: Any): Unit = {
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

  def setStyle(name: String, value: String): Unit = styles(name) = value
  def style(name: String): Option[String]         = styles.get(name)
  def removeStyle(name: String): Unit             = styles.remove(name)

  def setClassNames(names: Seq[String]): Unit =
    if (names.isEmpty) attrs.remove("class")
    else attrs("class") = names.mkString(" ")

  // Insertion goes through SsrNode, which explains why an insertion marker's position is no longer
  // found by linear search. See CHANGE.md P4-2.
  def insertChild(index: Int, child: HostNode): Unit =
    SsrNode.insertInto(children, index, child)

  def insertBefore(child: HostNode, before: Option[HostNode]): Unit =
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

  def removeChild(child: HostNode): Unit = {
    children -= child
    SsrNode.setHint(child, -1)
  }

  def clearChildren(): Unit = {
    children.foreach(SsrNode.setHint(_, -1))
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
