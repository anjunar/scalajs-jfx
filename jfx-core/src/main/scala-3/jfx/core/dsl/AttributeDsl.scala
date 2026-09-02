package jfx.core.dsl

trait AttributeDsl {

  def setAttribute(name: String, value: String): Unit

  def removeAttribute(name: String): Unit

  def attribute(name: String): Option[String]

}

object AttributeDsl {

  def setAttribute(name: String, value: String)(using component: AttributeDsl): Unit =
    component.setAttribute(name, value)

  def removeAttribute(name: String)(using component: AttributeDsl): Unit =
    component.removeAttribute(name)

  def attribute(name: String)(using component: AttributeDsl): Option[String] =
    component.attribute(name)

}
