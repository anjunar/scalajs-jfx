package ui.core.render

import org.scalajs.dom

final class DomCommentNode(private[ui] val node: dom.Comment) extends CommentNode {
  def text: String         = node.data
  def renderHtml(): String = s"<!--${node.data}-->"
}
