package jfx.core.layout

import jfx.core.component.{AbstractComponent, AbstractCustomComponent, Runtime}
import jfx.core.render.Cursor
import jfx.core.state.ReadOnlyProperty

class Condition(active: ReadOnlyProperty[Boolean], create: () => AbstractComponent) extends AbstractCustomComponent {
  private var mounted: Option[AbstractComponent] = None

  override def compose(cursor: Cursor): Unit = {
    def sync(value: Boolean): Unit =
      if (value && mounted.isEmpty) {
        mounted = Some(Runtime.mount(create(), cursor, Some(this)))
      } else if (!value) {
        mounted.foreach(Runtime.unmount)
        mounted = None
      }

    sync(active.get)
    addDisposable(active.observe(sync))
  }
}