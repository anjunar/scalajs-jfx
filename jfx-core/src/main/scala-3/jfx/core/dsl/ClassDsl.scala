package jfx.core.dsl

import jfx.core.state.ReadOnlyProperty

trait ClassDsl {

  def addClass(name: String): Unit

  def getClasses: Seq[String]

  def setClasses(values: Seq[String]): Unit

  def classCondition(name: String, condition: ReadOnlyProperty[Boolean]): Unit

}

object ClassDsl {

  def addClass(name: String)(using component: ClassDsl): Unit =
    component.addClass(name)

  def classes(using component: ClassDsl): Seq[String] =
    component.getClasses

  def classes_=(value: Seq[String])(using component: ClassDsl): Unit =
    component.setClasses(value)

  def classes_=(value: String)(using component: ClassDsl): Unit =
    component.setClasses(value.split("\\s+").filter(_.nonEmpty).toSeq)

  def classIf(name: String, condition: ReadOnlyProperty[Boolean])(using component: ClassDsl): Unit =
    component.classCondition(name, condition)

}
