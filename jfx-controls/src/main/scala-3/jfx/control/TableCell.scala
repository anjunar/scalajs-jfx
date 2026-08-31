package jfx.control

import jfx.core.component.AbstractComponent
import jfx.core.dsl.ClassDsl.{addClass, classIf}
import jfx.core.dsl.DslLayer
import jfx.core.layout.TextComponent.text
import jfx.core.render.Cursor
import jfx.core.state.Property

class TableCell[S, T] extends AbstractComponent {
  override val tagName: String = "div"

  val $itemProperty: Property[T | Null] = Property(null)
  val $emptyProperty: Property[Boolean] = Property(true)

  override def compose(cursor: Cursor): Unit =
    DslLayer.render(this, cursor) {
      addClass("jfx-table-cell")
      classIf("jfx-table-cell-empty", $emptyProperty)
      text($itemProperty.map(item => Option(item).fold("")(_.toString))) {}
    }

  private[control] def applyRenderedItem(item: T | Null, empty: Boolean): Unit = {
    $itemProperty.set(item)
    $emptyProperty.set(empty)
  }
}

object TableCell {
  def cell[S, T](
      body: TableCell[S, T] ?=> Cursor ?=> Unit = {}
  )(using AbstractComponent, Cursor): TableCell[S, T] =
    DslLayer.child(new TableCell[S, T]()) {
      body
    }
}
