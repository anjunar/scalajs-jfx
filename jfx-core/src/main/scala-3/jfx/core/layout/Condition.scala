package jfx.core.layout

import jfx.core.component.{AbstractComponent, AbstractCustomComponent, DynamicMountPoint, Runtime}
import jfx.core.render.Cursor
import jfx.core.state.ReadOnlyProperty

class Condition(active: ReadOnlyProperty[Boolean], create: () => AbstractComponent)
    extends AbstractCustomComponent {
  private var mounted: Option[AbstractComponent] = None

  override def compose(cursor: Cursor): Unit = {
    val mountPoint = new DynamicMountPoint(this, cursor)

    def sync(value: Boolean): Unit =
      if (value && mounted.isEmpty) {
        mounted = Some(Runtime.mount(create(), mountPoint.appendCursor, Some(this)))
      } else if (!value) {
        mounted.foreach(Runtime.unmount)
        mounted = None
      }

    sync(active.get)
    mountPoint.finishInitialComposition()
    addDisposable(active.observeWithoutInitial(sync))
  }
}

object Condition {
  def when(active: ReadOnlyProperty[Boolean])(
      body: AbstractComponent ?=> Cursor ?=> Unit
  )(using AbstractComponent, Cursor): Condition =
    DslLayer.child(
      new Condition(active, () => new ConditionalBody(body))
    ) {}

  private final class ConditionalBody(
      body: AbstractComponent ?=> Cursor ?=> Unit
  ) extends AbstractCustomComponent {
    override def compose(cursor: Cursor): Unit =
      body(using this)(using cursor)
  }
}
