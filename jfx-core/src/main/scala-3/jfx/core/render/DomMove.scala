package jfx.core.render

import org.scalajs.dom
import scala.scalajs.js

private[render] object DomMove {
  /** Keeps logical selection/focused form controls when insertBefore is the available move API.
    * Native IME state cannot be reconstructed: protect its host with a mutation guard instead.
    */
  def insert(parent: dom.Node, child: dom.Node, before: dom.Node): Unit = {
    if (child == before || (child.parentNode == parent && child.nextSibling == before)) return
    HostMutationGuard.checkDomInsertion(parent, child)
    val document = child.ownerDocument.asInstanceOf[dom.HTMLDocument]
    val targetDocument = if (parent.nodeType == dom.Node.DOCUMENT_NODE) parent else parent.ownerDocument
    require(document == targetDocument, "Moving across DOM documents is not supported.")
    if (child.parentNode == null) {
      parent.insertBefore(child, before)
      return
    }
    val active = document.activeElement
    val hadFocus = active != null && child.contains(active)
    val selection = Option(document.defaultView).map(_.getSelection()).orNull
    val restoreSelection = selection != null && selection.anchorNode != null &&
      (child.contains(selection.anchorNode) || child.contains(selection.focusNode))
    val anchor = if (restoreSelection) selection.anchorNode else null
    val focus = if (restoreSelection) selection.focusNode else null
    val anchorOffset = if (restoreSelection) selection.anchorOffset else 0
    val focusOffset = if (restoreSelection) selection.focusOffset else 0
    val control = if (hadFocus) active.asInstanceOf[js.Dynamic] else null
    val start = if (hadFocus) control.selectDynamic("selectionStart") else js.undefined
    val end = if (hadFocus) control.selectDynamic("selectionEnd") else js.undefined
    val direction = if (hadFocus) control.selectDynamic("selectionDirection") else js.undefined
    val dynamicParent = parent.asInstanceOf[js.Dynamic]
    if (js.typeOf(dynamicParent.selectDynamic("moveBefore")) == "function" &&
        parent.isConnected == child.isConnected && (child.nodeType == 1 || child.nodeType == 3))
      dynamicParent.applyDynamic("moveBefore")(child, before)
    else parent.insertBefore(child, before)

    if (hadFocus && document.activeElement != active)
      control.applyDynamic("focus")(js.Dynamic.literal(preventScroll = true))
    if (restoreSelection && anchor.isConnected && focus.isConnected)
      selection.asInstanceOf[js.Dynamic].applyDynamic("setBaseAndExtent")(
        anchor, anchorOffset, focus, focusOffset)
    // Restoring a range can focus its editing host, including when a nested control was active.
    if (restoreSelection && active != null && active.isConnected && document.activeElement != active)
      active.asInstanceOf[js.Dynamic].applyDynamic("focus")(js.Dynamic.literal(preventScroll = true))
    if (hadFocus && !js.isUndefined(start) && start != null)
      control.applyDynamic("setSelectionRange")(start, end, direction)
  }
}
