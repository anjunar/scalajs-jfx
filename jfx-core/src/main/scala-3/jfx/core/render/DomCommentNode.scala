package jfx.core.render

import org.scalajs.dom

final class DomCommentNode(private[jfx] val node: dom.Comment) extends CommentNode {
  def text: String = node.data
  def renderHtml(): String = s"<!--${node.data}-->"
}
