package jfx.core.dsl

import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.render.Cursor

object DslLayer {

  def render(root: AbstractComponent, cursor: Cursor)(
      body: AbstractComponent ?=> Cursor ?=> Unit
  ): Unit =
    body(using root)(using cursor)

  def renderInto[A <: AbstractComponent](target: A)(
      body: A ?=> Cursor ?=> Unit
  ): Unit = {
    val cursor = Option(target._contentCursor).getOrElse {
      throw new IllegalStateException(
        s"Component '${target.getClass.getSimpleName}' has no mounted content cursor."
      )
    }

    body(using target)(using cursor)
  }

  def child[A <: AbstractComponent](component: A)(
      body: A ?=> Cursor ?=> Unit
  )(using parent: AbstractComponent, cursor: Cursor): A = {
    val (mounted, childCursor) =
      Runtime.mountWithCursor(component, cursor, Some(parent))

    try {
      body(using mounted)(using childCursor)
      mounted
    } catch {
      case error: Throwable =>
        Runtime.unmount(mounted)
        throw error
    }
  }
}
