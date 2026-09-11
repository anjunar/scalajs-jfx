package ui.core.render

import org.scalajs.dom

/** Explicit browser interop for integrations such as selection and native input adapters.
  * Document writes should use UI hosts; raw DOM writes cannot be checked by mutation guards.
  */
object DomNodes {
  def option(node: HostNode): Option[dom.Node] = node match {
    case host: DomHostElement => Some(host.node)
    case text: DomTextNode => Some(text.node)
    case comment: DomCommentNode => Some(comment.node)
    case _ => None
  }

  def raw(node: HostNode): dom.Node =
    node match {
      case host: DomHostElement    => host.node
      case text: DomTextNode       => text.node
      case comment: DomCommentNode => comment.node
      case other                   =>
        throw new IllegalArgumentException(s"Not a browser DOM node: ${other.getClass.getName}")
    }

  /** Counterpart to [[raw]]: wraps a DOM node in the matching HostNode. */
  def wrap(node: dom.Node): HostNode =
    node.nodeType match {
      case kind if kind == dom.Node.ELEMENT_NODE => new DomHostElement(node.asInstanceOf[dom.Element])
      case kind if kind == dom.Node.TEXT_NODE => new DomTextNode(node.asInstanceOf[dom.Text])
      case kind if kind == dom.Node.COMMENT_NODE => new DomCommentNode(node.asInstanceOf[dom.Comment])
      case other =>
        throw new IllegalArgumentException(s"Unsupported DOM node type: $other")
    }
}
