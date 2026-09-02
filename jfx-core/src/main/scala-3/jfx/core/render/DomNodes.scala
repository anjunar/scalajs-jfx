package jfx.core.render

import org.scalajs.dom

private[jfx] object DomNodes {
  def raw(node: HostNode): dom.Node =
    node match {
      case host: DomHostElement    => host.node
      case text: DomTextNode       => text.node
      case comment: DomCommentNode => comment.node
      case other =>
        throw new IllegalArgumentException(s"Not a browser DOM node: ${other.getClass.getName}")
    }
}
