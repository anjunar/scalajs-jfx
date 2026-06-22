package jfx.core.statement

import jfx.core.component.AbstractComponent
import jfx.core.render.Cursor
import jfx.core.state.{ListProperty, ReadOnlyProperty}

private final class PropertyForeach[V](source: ReadOnlyProperty[Seq[V]],
                                       list: ListProperty[V],
                                       build: (V, Int) => AbstractComponent ?=> Cursor ?=> Unit,
                                       reindexOnStructuralChange: Boolean) extends Foreach[V](list, build, reindexOnStructuralChange) {
  override def compose(cursor: Cursor): Unit = {
    super.compose(cursor)
    addDisposable(source.observeWithoutInitial(values => list.setAll(values)))
  }
}
