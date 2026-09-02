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

  def clearChildren(): Unit = children.clear()

  def childCount: Int = children.length

  def renderHtml(): String = {
    val styleStr =
      if (styles.isEmpty) ""
      else s""" style="${styles.map { case (k, v) => s"$k: $v" }.mkString("; ")}""""

    val attrStr = attrs.map { case (k, v) => s""" $k="${escapeAttr(v)}"""" }.mkString
    val content = children.map(_.renderHtml()).mkString
    s"<$tagName$attrStr$styleStr>$content</$tagName>"
  }

  private def escapeAttr(value: String): String =
    value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")
}
