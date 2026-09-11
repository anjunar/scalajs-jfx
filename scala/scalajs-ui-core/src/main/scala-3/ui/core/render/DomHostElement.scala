package ui.core.render

import ui.core.state.Disposable
import org.scalajs.dom

import scala.scalajs.js

final class DomHostElement(private[ui] val node: dom.Element) extends HostElement {
  def tagName: String = node.tagName.toLowerCase

  def setAttribute(name: String, value: String): Unit = {
    if (attribute(name).contains(value)) return
    HostMutationGuard.checkWrite(this)
    node.setAttribute(name, value)
  }
  def removeAttribute(name: String): Unit = {
    if (attribute(name).isEmpty) return
    HostMutationGuard.checkWrite(this)
    node.removeAttribute(name)
  }
  def attribute(name: String): Option[String]         = Option(node.getAttribute(name))

  def setProperty(name: String, value: Any): Unit = {
    HostMutationGuard.checkRemoval(this)
    node.asInstanceOf[js.Dynamic].updateDynamic(name)(value.asInstanceOf[js.Any])
  }

  def property[T](name: String): Option[T] = {
    val value = node.asInstanceOf[js.Dynamic].selectDynamic(name)
    if (js.isUndefined(value) || value == null) None else Some(value.asInstanceOf[T])
  }

  def setStyle(name: String, value: String): Unit = {
    HostMutationGuard.checkWrite(this)
    node match {
      case html: dom.HTMLElement => html.style.setProperty(name, value)
      case _                     => ()
    }
  }

  def removeStyle(name: String): Unit = {
    HostMutationGuard.checkWrite(this)
    node match {
      case html: dom.HTMLElement => html.style.removeProperty(name)
      case _                     => ()
    }
  }

  /** The inline value, not the computed one — the same scope as `setStyle`. */
  def style(name: String): Option[String] =
    node match {
      case html: dom.HTMLElement => Option(html.style.getPropertyValue(name)).filter(_.nonEmpty)
      case _                     => None
    }

  def setClassNames(names: Seq[String]): Unit =
    if (names.isEmpty) removeAttribute("class")
    else setAttribute("class", names.mkString(" "))

  def insertChild(index: Int, child: HostNode): Unit = {
    val reference =
      if (index >= 0 && index < node.childNodes.length) node.childNodes.item(index)
      else null
    DomMove.insert(node, DomNodes.raw(child), reference)
  }

  def insertBefore(child: HostNode, before: Option[HostNode]): Unit =
    DomMove.insert(node, DomNodes.raw(child), before.map(DomNodes.raw).orNull)

  def removeChild(child: HostNode): Unit = {
    val rawChild = DomNodes.raw(child)
    if (rawChild.parentNode == node) {
      HostMutationGuard.checkRemoval(child)
      HostMutationGuard.checkWrite(this)
      node.removeChild(rawChild)
    }
  }

  def clearChildren(): Unit = {
    HostMutationGuard.checkRemoval(this)
    while (node.firstChild != null) node.removeChild(node.firstChild)
  }

  def childCount: Int = node.childNodes.length

  override def on(eventName: String)(handler: UiEvent => Unit): Disposable = {
    val listener: js.Function1[dom.Event, Unit] = event => handler(new DomUiEvent(event))
    node.addEventListener(eventName, listener)
    Disposable(node.removeEventListener(eventName, listener))
  }

  def renderHtml(): String = node.outerHTML
}
