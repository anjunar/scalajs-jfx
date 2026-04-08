package jfx.core.meta

import reflect.ReflectRegistry
import scala.collection.mutable
import scala.reflect.ClassTag

object ClassLoader {

  val classes : mutable.Map[String, (Class[?], () => Any)] = mutable.HashMap()

  def register[E](factory: () => E)(using ClassTag[E]): Unit = {
    val clazz = summon[ClassTag[E]].runtimeClass
    classes.put(clazz.getName, (clazz, factory))
    ReflectRegistry.registerRuntimeClass(clazz.getName, clazz)
  }

  def getFactory(typeName: String): Option[() => Any] = {
    classes.get(typeName).map(_._2)
  }

  def getClassByTypeName(typeName: String): Option[Class[?]] = {
    classes.get(typeName).map(_._1)
  }

  def containsTypeName(typeName: String): Boolean = {
    classes.exists(_._1 == typeName)
  }

}
