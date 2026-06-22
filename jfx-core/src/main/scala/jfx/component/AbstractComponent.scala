package jfx.component

import jfx.dsl.ComponentDSL
import jfx.render.*
import jfx.state.{CompositeDisposable, Disposable}

import scala.collection.mutable

abstract class AbstractComponent extends ComponentDSL {

  val tagName: String

  private[jfx] var _host: HostNode = _
  private[jfx] var _parent: Option[AbstractComponent] = None
  private[jfx] val _children = mutable.ArrayBuffer.empty[AbstractComponent]
  private[jfx] val disposables = new CompositeDisposable()

  private val baseClasses = mutable.ArrayBuffer.empty[String]
  private val userClasses = mutable.ArrayBuffer.empty[String]

  def host: HostElement = _host match {
    case h: HostElement => h
    case _ =>
      throw new IllegalStateException(
        s"Component '${getClass.getSimpleName}' (tagName='$tagName') hat kein HostElement. " +
          "Virtuelle Komponenten müssen über den Parent zugreifen."
      )
  }

  def parent: Option[AbstractComponent] = _parent
  def children: Seq[AbstractComponent] = _children.toSeq
  def isVirtual: Boolean = tagName.isEmpty
  def isText: Boolean = tagName == "#text"
  def isBound: Boolean = _host != null

  def domNodeCount: Int =
    if (!isVirtual) 1
    else virtualAnchorCount + _children.map(_.domNodeCount).sum

  def domOffset: Int = _parent match {
    case None => 0
    case Some(p) =>
      val siblingsBefore = p._children.takeWhile(_ ne this)
      val local = siblingsBefore.map(_.domNodeCount).sum
      if (p.isVirtual) p.domOffset + local else local
  }

  def physicalHosts: Seq[HostNode] =
    if (!isVirtual && _host != null) Seq(_host)
    else virtualStart.toSeq ++ _children.flatMap(_.physicalHosts).toSeq ++ virtualEnd.toSeq

  private def virtualStart: Option[CommentNode] = _host match {
    case host: VirtualHost => host.start
    case _ => None
  }

  private def virtualEnd: Option[CommentNode] = _host match {
    case host: VirtualHost => host.end
    case _ => None
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
  }
}