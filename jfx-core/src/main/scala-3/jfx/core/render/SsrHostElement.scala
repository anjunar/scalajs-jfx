package jfx.core.render

import scala.collection.mutable

final class SsrHostElement(val tagName: String) extends HostElement {
  private val attrs = mutable.LinkedHashMap.empty[String, String]
  private val styles = mutable.LinkedHashMap.empty[String, String]
  private val children = mutable.ArrayBuffer.empty[HostNode]

  def setAttribute(name: String, value: String): Unit = attrs(name) = value
  def removeAttribute(name: String): Unit = attrs.remove(name)
  def attribute(name: String): Option[String] = attrs.get(name)

  def setStyle(name: String, value: String): Unit = styles(name) = value
  def removeStyle(name: String): Unit = styles.remove(name)

  def setClassNames(names: Seq[String]): Unit =
    if (names.isEmpty) attrs.remove("class")
    else attrs("class") = names.mkString(" ")

  def insertChild(index: Int, child: HostNode): Unit = {
    val safeIndex = index.max(0).min(children.length)
    if (safeIndex == children.length) children += child
    else children.insert(safeIndex, child)
  }

  def insertBefore(child: HostNode, before: Option[HostNode]): Unit =
    before match {
      case Some(node) =>
        val idx = children.indexOf(node)
        if (idx >= 0) insertChild(idx, child)
        else children += child
      case None =>
        children += child
    }

  def removeChild(child: HostNode): Unit = children -= child
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
