package jfx.render

final class SsrCommentNode(val text: String) extends CommentNode {
  def renderHtml(): String = s"<!--${escapeComment(text)}-->"

  private def escapeComment(value: String): String =
    value.replace("--", "- -")
}
