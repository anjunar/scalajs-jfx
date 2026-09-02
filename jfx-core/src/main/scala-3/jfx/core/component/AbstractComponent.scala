package jfx.core.component

import jfx.core.dsl.{AttributeDsl, ClassDsl, EventDsl, PropertyDsl, StyleDsl}
import jfx.core.render.{CommentNode, Cursor, HostElement, HostNode, UiEvent, VirtualHost}
import jfx.core.state.{CompositeDisposable, Disposable, ReadOnlyProperty}
import org.scalajs.dom

import scala.scalajs.js

import scala.collection.mutable

abstract class AbstractComponent
    extends ClassDsl
    with EventDsl
    with AttributeDsl
    with PropertyDsl
    with StyleDsl {

  val tagName: String

  private[jfx] var _host: HostNode                    = _
  private[jfx] var _parent: Option[AbstractComponent] = None
  private[jfx] var _mountParentHost: Option[HostElement] = None
  private[jfx] var _contentCursor: Cursor = _
  private[jfx] val _children                          = mutable.ArrayBuffer.empty[AbstractComponent]
  private[jfx] val disposables                        = new CompositeDisposable()
  private[jfx] val _contextValues                     = mutable.HashMap.empty[AnyRef, AnyRef]

  private val baseClasses = mutable.ArrayBuffer.empty[String]
  private val userClasses = mutable.ArrayBuffer.empty[String]

  def host: HostElement = _host match {
    case h: HostElement => h
    case _ =>
      throw new IllegalStateException(
        s"Component '${getClass.getSimpleName}' (tagName='$tagName') has no HostElement. " +
          "Virtual components must access it through the parent."
      )
  }

  def parent: Option[AbstractComponent] = _parent
  def children: Seq[AbstractComponent]  = _children.toSeq
  def isVirtual: Boolean                = tagName.isEmpty
  def isText: Boolean                   = tagName == "#text"
  def isBound: Boolean                  = _host != null

  def domNodeCount: Int =
    if (!isVirtual) 1
    else virtualAnchorCount + _children.map(_.domNodeCount).sum

  def domOffset: Int = _parent match {
    case None => 0
    case Some(p) =>
      val siblingsBefore = p._children.takeWhile(_ ne this)
      val local          = siblingsBefore.map(_.domNodeCount).sum
      if (p.isVirtual) p.domOffset + local else local
  }

  def physicalHosts: Seq[HostNode] =
    if (!isVirtual && _host != null) Seq(_host)
    else
      virtualStart.toSeq ++ adoptedNodes ++
        _children.flatMap(_.physicalHosts).toSeq ++ virtualEnd.toSeq

  /**
   * Uebernimmt diese Komponente beim Hydrieren den server-gerenderten Inhalt
   * ungeprueft, statt ihn nachzubauen?
   *
   * Nur fuer Komponenten gedacht, die ihren Inhalt erst spaeter kennen -- etwa
   * eine Route, deren Loader noch laeuft. Siehe CHANGE.md P4-1.
   */
  private[jfx] def adoptsHydratedContent: Boolean = false

  private def adoptedNodes: Seq[HostNode] = _host match {
    case host: VirtualHost => host.adopted
    case _                 => Nil
  }

  private def virtualStart: Option[CommentNode] = _host match {
    case host: VirtualHost => host.start
    case _                 => None
  }

  private def virtualEnd: Option[CommentNode] = _host match {
    case host: VirtualHost => host.end
    case _                 => None
  }

  private def virtualAnchorCount: Int =
    virtualStart.size + virtualEnd.size

  def compose(cursor: Cursor): Unit = ()

  def afterCompose(cursor: Cursor): Unit = ()

  def addClass(name: String): Unit = {
    if (!baseClasses.contains(name)) {
      baseClasses += name
      syncClasses()
    }
  }

  def removeClass(name: String): Unit = {
    val idx = baseClasses.indexOf(name)
    if (idx >= 0) {
      baseClasses.remove(idx)
      syncClasses()
    }
  }
  
  def getClasses : Seq[String] = userClasses.toSeq

  def setClasses(names: Seq[String]): Unit = {
    userClasses.clear()
    userClasses ++= names
    syncClasses()
  }

  private[jfx] def hostBound(): Unit =
    syncClasses()

  private def syncClasses(): Unit =
    if (!isVirtual && !isText && _host != null)
      host.setClassNames((baseClasses ++ userClasses).distinct.toSeq)

  def addDisposable(d: Disposable): Unit = disposables.add(d)

  def dispose(): Unit = {
    _children.foreach(_.dispose())
    _children.clear()
    disposables.dispose()
    _host = null
    _parent = None
    _mountParentHost = None
    _contentCursor = null
  }

  def classCondition(name: String, condition: ReadOnlyProperty[Boolean]): Unit =
    addDisposable {
      condition.observe { enabled =>
        if (enabled) addClass(name)
        else removeClass(name)
      }
    }

  def onHandler(eventName: String)(handler: UiEvent => Unit): Unit =
    addDisposable(host.on(eventName)(handler))

  def onClickHandler(handler: UiEvent => Unit): Unit =
    onHandler("click")(handler)

  def onDoubleClickHandler(handler: UiEvent => Unit): Unit =
    onHandler("dblclick")(handler)

  def onWindowKeyDownHandler(handler: dom.KeyboardEvent => Unit): Unit =
    browserWindow.foreach { window =>
      val listener: js.Function1[dom.KeyboardEvent, Any] = event => handler(event)
      window.addEventListener("keydown", listener)
      addDisposable(Disposable(window.removeEventListener("keydown", listener)))
    }

  def onDisposable(eventName: String)(handler: UiEvent => Unit): Disposable =
    host.on(eventName)(handler)

  def setAttribute(name: String, value: String): Unit =
    host.setAttribute(name, value)

  def removeAttribute(name: String): Unit =
    host.removeAttribute(name)

  def attribute(name: String): Option[String] =
    host.attribute(name)

  def setProperty(name: String, value: Any): Unit =
    host.setProperty(name, value)

  def property[T](name: String): Option[T] =
    host.property[T](name)

  def setStyle(name: String, value: String): Unit =
    host.setStyle(name, value)

  def removeStyle(name: String): Unit =
    host.removeStyle(name)

  private def browserWindow: Option[dom.Window] =
    Option.when(js.typeOf(js.Dynamic.global.selectDynamic("window")) != "undefined")(dom.window)

}

object AbstractComponent {
  def addDisposable(d: Disposable)(using component: AbstractComponent): Unit =
    component.addDisposable(d)
}