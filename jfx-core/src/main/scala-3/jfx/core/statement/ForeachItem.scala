package jfx.core.statement

import jfx.core.component.{AbstractComponent, AbstractCustomComponent}
import jfx.core.render.Cursor

private final class ForeachItem[V](
    initialValue: V,
    index: Int,
    build: (V, Int) => AbstractComponent ?=> Cursor ?=> Unit
) extends AbstractCustomComponent {
  private var value: V = initialValue

  def updateValue(next: V): Unit =
    value = next

  override def compose(cursor: Cursor): Unit =
    build(value, index)(using this)(using cursor)
}
