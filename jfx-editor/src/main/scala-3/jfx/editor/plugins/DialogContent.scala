package jfx.editor.plugins

import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.dsl.DslLayer
import jfx.core.render.{Cursor, DomCursor, DomHostElement}
import org.scalajs.dom.HTMLElement

import scala.scalajs.js

private[plugins] abstract class DialogContent extends AbstractComponent {
  final override val tagName: String = "div"

  final def htmlElement: HTMLElement =
    host match {
      case domHost: DomHostElement => domHost.node.asInstanceOf[HTMLElement]
      case _                       =>
        throw new IllegalStateException("Dialog content is not mounted in a browser DOM.")
    }
}

private[plugins] object DialogContent {
  private val ownerProperty = "__jfxDialogContentOwner"

  def mount(content: DialogContent): HTMLElement = {
    Runtime.mount(content, DomCursor.detached())
    val element = content.htmlElement
    element.asInstanceOf[js.Dynamic].updateDynamic(ownerProperty)(content.asInstanceOf[js.Any])
    element
  }

  def dispose(element: HTMLElement): Unit = {
    val dynamic = element.asInstanceOf[js.Dynamic]
    val owner   = dynamic.selectDynamic(ownerProperty)
    if (!js.isUndefined(owner) && owner != null) {
      dynamic.updateDynamic(ownerProperty)(js.undefined)
      Runtime.unmount(owner.asInstanceOf[DialogContent])
    }
  }
}

private[plugins] final class DialogElement(override val tagName: String) extends AbstractComponent

private[plugins] object DialogElement {
  def element(tagName: String)(body: DialogElement ?=> Cursor ?=> Unit = {})(using
      AbstractComponent,
      Cursor
  ): DialogElement =
    DslLayer.child(new DialogElement(tagName))(body)
}
