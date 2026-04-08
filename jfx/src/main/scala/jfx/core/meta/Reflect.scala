package jfx.core.meta

import reflect.*
import reflect.macros.ReflectMacros
import scala.reflect.ClassTag

object Reflect {
  
  private val defaultLoader: ReflectClassLoader = ReflectClassLoader.create()
  
  def classLoader: ReflectClassLoader = defaultLoader
  
  def classLoaderForPackage(pkg: String): ReflectClassLoader = {
    val loader = ReflectClassLoader.createWithParent(defaultLoader)
    loader
  }
  
  inline def register[T](inline factory: () => T)(using ClassTag[T]): ClassDescriptor = {
    val descriptor = ReflectMacros.reflect[T]
    defaultLoader.register[T](descriptor, factory)
    descriptor
  }
  
  inline def registerAll[Ts](inline factories: (() => Ts)*)(using ClassTag[Ts]): Seq[ClassDescriptor] = {
    factories.map { factory =>
      val descriptor = ReflectMacros.reflect[Ts]
      defaultLoader.register[Ts](descriptor, factory)
      descriptor
    }
  }
  
  def loadClass[T](using Manifest[T]): Option[ClassDescriptor] =
    defaultLoader.loadClass[T]
  
  def loadClass(typeName: String): Option[ClassDescriptor] =
    defaultLoader.loadClass(typeName)
  
  def createInstance[T](typeName: String): Option[T] =
    defaultLoader.createInstanceAs[T](typeName)
  
  def getSubTypes[T](using Manifest[T]): List[ClassDescriptor] =
    defaultLoader.getSubTypes[T]
}
