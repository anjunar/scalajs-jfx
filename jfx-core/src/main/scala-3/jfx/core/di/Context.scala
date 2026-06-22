// jfx-core/src/main/scala/jfx/di/Context.scala
package jfx.core.di

import jfx.core.component.AbstractComponent

final class Context[A](val name: String) {

  private val key = this

  def provide(value: A)(using component: AbstractComponent): Unit =
    component._contextValues(key) = value.asInstanceOf[AnyRef]

  def inject(using component: AbstractComponent): Option[A] =
    findInTree(component).asInstanceOf[Option[A]]

  private def findInTree(component: AbstractComponent): Option[AnyRef] =
    component._contextValues.get(key) match {
      case some @ Some(_) => some
      case None => component._parent.flatMap(findInTree)
    }
}

object Context {
  def create[A](name: String): Context[A] = new Context[A](name)
}