package jfx.core.statement

import jfx.core.component.{AbstractComponent, AbstractCustomComponent, Runtime}
import jfx.core.dsl.DslLayerTwo
import jfx.core.render.{Cursor, HostNode, VirtualHost}
import jfx.core.state.ReadOnlyProperty

class DynamicComponentRenderer(
    componentProperty: ReadOnlyProperty[AbstractComponent]
) extends AbstractCustomComponent {

  private var mounted: Option[AbstractComponent] = None
  private var mountedCursor: Cursor              = _
  private var initialMount                       = true

  override def compose(cursor: Cursor): Unit = {
    mountedCursor = cursor

    replace(componentProperty.get)

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
    val cursor = insertionCursor

    mounted = Some(component)

    try {
      Runtime.mount(component, cursor, Some(this))
      initialMount = false
    } catch {
      case error: Throwable =>
        mounted = None
        throw error
    }
  }

  private def insertionCursor: Cursor =
    if (initialMount && mountedCursor.isHydrating) {
      mountedCursor
    } else {
      mounted match {
        case Some(component) =>
          firstHost(component).map(mountedCursor.before).getOrElse(endCursor)

        case None =>
          endCursor
      }
    }

  private def firstHost(component: AbstractComponent): Option[HostNode] =
    component.physicalHosts.headOption

  private def endCursor: Cursor =
    _host match {
      case host: VirtualHost =>
        host.end match {
          case Some(end) => mountedCursor.before(end)
          case None      => host.cursor.getOrElse(mountedCursor)
        }

      case _ =>
        mountedCursor
    }
}

object DynamicComponentRenderer {

  def dynamic(
      component: ReadOnlyProperty[AbstractComponent]
  )(using parent: AbstractComponent, cursor: Cursor): DynamicComponentRenderer =
    DslLayerTwo.child(new DynamicComponentRenderer(component)) {}
}
