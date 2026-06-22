package jfx.dsl

import jfx.component.AbstractComponent
import jfx.render.Cursor

object JfxDsl {

  private var current: AbstractComponent = _

  def render(root: AbstractComponent, cursor: Cursor)(body: Cursor ?=> Unit): Unit =
    root.withCursor(cursor) {
      val previous = current
      current = root

      try body(using cursor)
      finally current = previous
    }

  def child[A <: AbstractComponent](component: A)(body: A ?=> Cursor ?=> Unit)(using cursor: Cursor): A = {
    val parent = current

    given AbstractComponent = parent

    parent.child(component) {
      val previous = current
      current = component

      given A = component

      try body(using component)(using cursor)
      finally current = previous
    }

    component
  }
}