package jfx.render

import jfx.state.Disposable

trait HostElement extends HostNode {
  def tagName: String
  def setAttribute(name: String, value: String): Unit
  def removeAttribute(name: String): Unit
  def attribute(name: String): Option[String]
  def setStyle(name: String, value: String): Unit
  def removeStyle(name: String): Unit
  def setClassNames(names: Seq[String]): Unit
  def insertChild(index: Int, child: HostNode): Unit
  def insertBefore(child: HostNode, before: Option[HostNode]): Unit
  def removeChild(child: HostNode): Unit
  def clearChildren(): Unit
  def childCount: Int
  def on(eventName: String)(handler: UiEvent => Unit): Disposable = Disposable.empty
  def onClick(handler: UiEvent => Unit): Disposable = on("click")(handler)
}




