package ui.control.table

import org.scalajs.dom

/** Only the table's own focus target handles navigation; editors and header/menu controls own
  * theirs.
  */
private[table] object TableRowKeyboard {
  private var sequence        = 0L
  def nextRowPrefix(): String = {
    sequence += 1
    val prefix = s"ui-focused-row-$sequence-"
    if (dom.document.querySelector(s"[id^='$prefix']") != null) nextRowPrefix() else prefix
  }
  def handle[S](table: TableView[S], key: dom.KeyboardEvent, pageRows: Int): Unit = {
    if (key.defaultPrevented || key.isComposing || key.altKey || !table.canMoveColumns) return
    val count = math.max(0, table.items.totalLength)
    if (count == 0 || table.visibleLeafColumns.get.isEmpty) return
    val focus    = table.focusModel
    val previous = focus.focusedIndex
    val shortcut = key.ctrlKey || key.metaKey
    val next     = key.key match {
      case "ArrowDown" => Some(if (previous < 0) 0 else math.min(count - 1, previous + 1))
      case "ArrowUp"   => Some(math.max(0, previous - 1))
      case "Home"      => Some(0)
      case "End"       => Some(count - 1)
      case "PageDown"  =>
        Some(math.min(count.toLong - 1, math.max(0, previous).toLong + pageRows).toInt)
      case "PageUp" => Some(math.max(0, previous - pageRows))
      case _        => None
    }
    next match {
      case Some(index) =>
        key.preventDefault(); key.stopPropagation()
        focus.focus(index)
        if (!shortcut || key.shiftKey)
          table.selectionModel.click(index, shortcut, key.shiftKey, previous)
        table.scrollTo(index)
      case None if key.key == " " && previous >= 0 =>
        key.preventDefault(); key.stopPropagation()
        table.selectionModel.click(previous, shortcut, key.shiftKey, previous)
      case None if shortcut && key.key.toLowerCase == "a" =>
        key.preventDefault(); key.stopPropagation()
        table.selectionModel.selectAll()
      case _ => ()
    }
  }

  def isRowBackground(event: dom.MouseEvent, row: dom.Element): Boolean = {
    if (event.defaultPrevented) return false
    var target = event.target match {
      case element: dom.Element => element
      case _                    => return false
    }
    while (target != null && target != row) {
      if (
        target.matches(
          "input,textarea,select,button,a[href],[contenteditable]:not([contenteditable=false]),[tabindex],[role=button],[role=checkbox],[role=combobox],[role=textbox],[role=slider],[role=spinbutton]"
        )
      )
        return false
      target = target.parentNode match {
        case element: dom.Element => element
        case _                    => null
      }
    }
    target == row
  }
}
