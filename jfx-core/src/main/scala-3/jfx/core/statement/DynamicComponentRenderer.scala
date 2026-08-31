package jfx.core.statement

import jfx.core.component.{AbstractComponent, AbstractCustomComponent, DynamicMountPoint, Runtime}
import jfx.core.dsl.DslLayer
import jfx.core.render.Cursor
import jfx.core.state.ReadOnlyProperty

class DynamicComponentRenderer(
    componentProperty: ReadOnlyProperty[AbstractComponent]
) extends AbstractCustomComponent {

  private var mounted: Option[AbstractComponent] = None
  private var mountPoint: DynamicMountPoint      = _

  override def compose(cursor: Cursor): Unit = {
    mountPoint = new DynamicMountPoint(this, cursor)

    replace(componentProperty.get)
    mountPoint.finishInitialComposition()

    addDisposable {
      componentProperty.observeWithoutInitial { component =>
        replace(component)
      }
    }
  }

  private def replace(component: AbstractComponent): Unit = {
    mounted match {
      case Some(current) if current eq component =>
        ()

      case Some(current) =>
        Runtime.unmount(current)
        mounted = None
        mount(component)

      case None =>
        mount(component)
    }
  }

  private def mount(component: AbstractComponent): Unit = {
    mounted = Some(component)

    try {
      Runtime.mount(component, mountPoint.appendCursor, Some(this))
    } catch {
      case error: Throwable =>
        mounted = None
        throw error
    }
  }

}

object DynamicComponentRenderer {

  def dynamic(
      component: ReadOnlyProperty[AbstractComponent]
  )(using parent: AbstractComponent, cursor: Cursor): DynamicComponentRenderer =
    DslLayer.child(new DynamicComponentRenderer(component)) {}
}
