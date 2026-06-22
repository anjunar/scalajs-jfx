package jfx.core.dsl

import jfx.core.component.{AbstractComponent, Runtime}
import jfx.core.render.Cursor

trait DslLayerOne { self: AbstractComponent =>

  inline def withCursor[A](cursor: Cursor)(inline block: (Cursor, AbstractComponent) ?=> A): A =
    given Cursor = cursor
    given AbstractComponent = this
    block

  def child[C <: AbstractComponent](component: C)
                                   (using cursor: Cursor, parent: AbstractComponent): C =
    Runtime.mount(component, cursor, Some(parent))

  def child[C <: AbstractComponent](component: C)(build: DslLayerOne.Scope[C] ?=> Unit)
                                   (using cursor: Cursor, parent: AbstractComponent): C = {
    val mounted = Runtime.mount(component, cursor, Some(parent))
    given Cursor = cursor.sub(mounted.host)
    given AbstractComponent = mounted
    given DslLayerOne.Scope[C] = new DslLayerOne.Scope(mounted)
    build
    mounted
  }

}

object DslLayerOne {
  final class Scope[C <: AbstractComponent](val component: C)

  def it[C <: AbstractComponent](using scope: Scope[C]): C = scope.component
}