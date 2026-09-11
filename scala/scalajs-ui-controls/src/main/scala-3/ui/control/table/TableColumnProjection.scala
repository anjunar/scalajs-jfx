package ui.control.table

import ui.core.component.{AbstractComponent, AbstractCustomComponent}
import ui.core.dsl.DslLayer
import ui.core.layout.Div
import ui.core.render.Cursor
import ui.core.statement.KeyedChildren

/** Physical, box-less slots let Runtime move even virtual/custom cell renderers intact. */
private[table] final class TableColumnProjection[S](
    table: TableView[S],
    body: TableColumn[S, ?] => AbstractComponent ?=> Cursor ?=> Unit
) extends AbstractCustomComponent {
  override def compose(cursor: Cursor): Unit = DslLayer.render(this, cursor) {
    val initial = table.visibleLeafColumns.get
    val keyed   = DslLayer.child(
      new KeyedChildren[TableColumn[S, ?], TableColumn[S, ?], Div](
        initial,
        column => column,
        column =>
          new Div {
            override def compose(cursor: Cursor): Unit = DslLayer.render(this, cursor) {
              addClass("ui-table-column-slot")
              setStyle("display", "contents")
              body(column)
            }
          },
        (_, _) => ()
      )
    ) {}
    var ready = !cursor.isHydrating
    addDisposable(table.visibleLeafColumns.observeWithoutInitial { columns =>
      if (ready) keyed.setItems(columns)
    })
    cursor.afterHydration { () =>
      if (!isDisposed) {
        ready = true
        if (table.visibleLeafColumns.get != initial) keyed.setItems(table.visibleLeafColumns.get)
      }
    }
  }
}
