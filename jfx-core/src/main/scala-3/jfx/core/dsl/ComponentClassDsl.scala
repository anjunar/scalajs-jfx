package jfx.core.dsl

import jfx.core.component.AbstractComponent
import jfx.core.state.ReadOnlyProperty

trait ComponentClassDsl {

  def classIf(name: String, condition: ReadOnlyProperty[Boolean])(using component: AbstractComponent): Unit =
    component.addDisposable {
      condition.observe { enabled =>
        if (enabled) component.addClass(name)
        else component.removeClass(name)
      }
    }
}
