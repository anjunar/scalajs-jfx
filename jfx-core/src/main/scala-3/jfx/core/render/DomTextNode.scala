package jfx.core.render

import org.scalajs.dom

final class DomTextNode(private[jfx] val node: dom.Text) extends TextNode {
  def setText(value: String): Unit =
    if (node.data != value) {
      HostMutationGuard.checkWrite(this)
      node.data = value
    }

  override def spliceText(start: Int, deleteCount: Int, inserted: String): Unit = {
    TextNode.checkSplice(node.data, start, deleteCount)
    if (node.data.substring(start, start + deleteCount) != inserted) {
      HostMutationGuard.checkWrite(this)
      node.replaceData(start, deleteCount, inserted)
    }
  }
  def getText: String              = node.data
  def renderHtml(): String         = node.data
}
