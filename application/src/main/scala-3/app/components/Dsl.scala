package app.components

import jfx.core.component.AbstractComponent
import jfx.core.render.UiEvent
import jfx.core.state.ReadOnlyProperty

object Dsl {
  def addClass(name: String)(using component: AbstractComponent): Unit =
    component.addClass(name)

  def classes(using component: AbstractComponent): Seq[String] =
    Seq.empty

  def classes_=(value: Seq[String])(using component: AbstractComponent): Unit =
    component.setClasses(value)

  def classes_=(value: String)(using component: AbstractComponent): Unit =
    component.setClasses(value.split("\\s+").filter(_.nonEmpty).toSeq)

  def classIf(name: String, condition: ReadOnlyProperty[Boolean])(using component: AbstractComponent): Unit =
    component.classIf(name, condition)

  def onClick(handler: UiEvent => Unit)(using component: AbstractComponent): Unit =
    component.onClick(handler)
}
