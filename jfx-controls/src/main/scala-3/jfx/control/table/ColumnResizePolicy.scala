package jfx.control.table

/** Compensation strategy for user/API column resizing. All but Unconstrained fit the viewport. */
enum ColumnResizePolicy {
  case Unconstrained, AllColumns, LastColumn, NextColumn, SubsequentColumns, FlexNextColumn,
    FlexLastColumn
}
