package jfx.core.dsl

import jfx.core.state.ReadOnlyProperty

trait ClassDsl {

  def classIf(name: String, condition: ReadOnlyProperty[Boolean]): Unit

}

object ClassDsl {

  def classIf(name: String, condition: ReadOnlyProperty[Boolean])(using component: ClassDsl): Unit =
    component.classIf(name, condition)

}
