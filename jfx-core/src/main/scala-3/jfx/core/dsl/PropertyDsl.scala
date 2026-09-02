package jfx.core.dsl

trait PropertyDsl {

  def setProperty(name: String, value: Any): Unit

  def property[T](name: String): Option[T]

}

object PropertyDsl {

  def setProperty(name: String, value: Any)(using component: PropertyDsl): Unit =
    component.setProperty(name, value)

  def property[T](name: String)(using component: PropertyDsl): Option[T] =
    component.property[T](name)

}
