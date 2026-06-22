package jfx.core.dsl

import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.render.{Cursor, HostElement, VirtualHost}

object DslLayerTwo {

  def render(root: AbstractComponent, cursor: Cursor)(
    body: AbstractComponent ?=> Cursor ?=> Unit
  ): Unit =
    root.withCursor(cursor) {
      given AbstractComponent = root

      body(using root)(using cursor)
    }

  def child[A <: AbstractComponent](component: A)(
    body: A ?=> Cursor ?=> Unit
  )(using parent: AbstractComponent, cursor: Cursor): A = {
    val mounted = Runtime.mount(component, cursor, Some(parent))

    val childCursor =
      mounted._host match {
        case host: VirtualHost => host.cursor.getOrElse(cursor)
        case host: HostElement => cursor.sub(host)
        case _ => cursor
      }

    body(using mounted)(using childCursor)

    mounted
  }
}