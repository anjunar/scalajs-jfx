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

  /** Gegenstueck zu [[raw]]: huellt einen DOM-Knoten in den passenden HostNode. */
  def wrap(node: dom.Node): HostNode =
    node match {
      case element: dom.Element => new DomHostElement(element)
      case text: dom.Text       => new DomTextNode(text)
      case comment: dom.Comment => new DomCommentNode(comment)
      case other =>
        throw new IllegalArgumentException(s"Unsupported DOM node type: ${other.nodeType}")
    }
}
