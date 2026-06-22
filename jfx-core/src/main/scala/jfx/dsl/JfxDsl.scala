package jfx.dsl

import jfx.component.AbstractComponent
import jfx.render.Cursor

object JfxDsl {

  private var current: AbstractComponent = _

  def render(cursor: Cursor)(body: => Unit)(using root: AbstractComponent): Unit = {
    root.withCursor(cursor) {
      val previous = current
      current = root
      body
      current = previous
    }
  }
  
  def child[A <: AbstractComponent](component: A)(body: Cursor ?=> Unit)(using cursor: Cursor): A = {
    val parent = current

    given AbstractComponent = parent

    parent.child(component) {
      val previous = current
      current = component

      try body(using cursor)
      finally current = previous
    }

    component
  }
}