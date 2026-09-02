package jfx.core.render

final class VirtualHost(
    val parentElement: Option[HostElement],
    val start: Option[CommentNode] = None,
    val end: Option[CommentNode] = None,
    val cursor: Option[Cursor] = None,
    /** Uebernommene Knoten zwischen den Ankern; siehe [[VirtualRange]]. */
    val adopted: Seq[HostNode] = Nil
) extends HostNode {
  def renderHtml(): String = ""
}
