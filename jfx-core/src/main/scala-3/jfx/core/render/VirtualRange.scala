package jfx.core.render

/** A range delimited by comment anchors.
  *
  * `adopted` holds nodes between the anchors when the range was adopted rather than claimed. They
  * then belong to the component and are removed with it -- otherwise they would remain in the DOM
  * when it is replaced.
  */
final case class VirtualRange(
    start: CommentNode,
    end: CommentNode,
    cursor: Cursor,
    adopted: Seq[HostNode] = Nil
)
