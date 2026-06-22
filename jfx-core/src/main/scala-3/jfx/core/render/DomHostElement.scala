package jfx.core.render

import jfx.core.state.Disposable
import org.scalajs.dom

final class DomHostElement(private[jfx] val node: dom.Element) extends HostElement {
  def tagName: String = node.tagName.toLowerCase

  def setAttribute(name: String, value: String): Unit = node.setAttribute(name, value)
  def removeAttribute(name: String): Unit = node.removeAttribute(name)
  def attribute(name: String): Option[String] = Option(node.getAttribute(name))

  def setStyle(name: String, value: String): Unit =
    node match {
      case html: dom.HTMLElement => html.style.setProperty(name, value)
      case _ => ()
    }

  def removeStyle(name: String): Unit =
    node match {
      case html: dom.HTMLElement => html.style.removeProperty(name)
      case _ => ()
    }

  def setClassNames(names: Seq[String]): Unit =
    if (names.isEmpty) node.removeAttribute("class")
    else node.setAttribute("class", names.mkString(" "))

  def insertChild(index: Int, child: HostNode): Unit = {
    val reference =
      if (index >= 0 && index < node.childNodes.length) node.childNodes.item(index)
      else null
    node.insertBefore(DomNodes.raw(child), reference)
  }

  def insertBefore(child: HostNode, before: Option[HostNode]): Unit =
    node.insertBefore(DomNodes.raw(child), before.map(DomNodes.raw).orNull)

  def removeChild(child: HostNode): Unit = {
    val rawChild = DomNodes.raw(child)
    if (rawChild.parentNode == node) node.removeChild(rawChild)
  }

  def clearChildren(): Unit =
    while (node.firstChild != null) node.removeChild(node.firstChild)

  def childCount: Int = node.childNodes.length

  override def on(eventName: String)(handler: UiEvent => Unit): Disposable = {
    val listener: dom.Event => Unit = event => handler(new DomUiEvent(event))
    node.addEventListener(eventName, listener)
    Disposable(node.removeEventListener(eventName, listener))
  }

  def renderHtml(): String = node.outerHTML
}

