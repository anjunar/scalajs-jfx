package jfx.dsl

import jfx.component.AbstractComponent
import jfx.render.Cursor

object JfxDsl {

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
    given AbstractComponent = component

    parent.child(component) {
      given A = component

      body(using component)(using cursor)
    }
    component
  }
}