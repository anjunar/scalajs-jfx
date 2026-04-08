package jfx.core.meta

import reflect.*
import reflect.macros.ReflectMacros
import scala.collection.mutable
import scala.reflect.ClassTag

class PackageClassLoader(val packageName: String, parent: ReflectClassLoader) {

  private val loader: ReflectClassLoader = ReflectClassLoader.createWithParent(parent)

  inline def register[T](inline factory: () => T, clazz : Class[T])(using ClassTag[T]): ClassDescriptor = {
    val descriptor = ReflectMacros.reflectWithAccessors[T]
    descriptor.bindRuntimeClass(clazz)
    loader.register[T](descriptor, factory)
    descriptor
  }

  def registerByDescriptor(descriptor: ClassDescriptor): Unit =
    loader.registerByTypeName(descriptor.typeName, descriptor)

  def loadClass(typeName: String): Option[ClassDescriptor] =
    loader.loadClass(typeName)

  def createInstance[T](typeName: String): Option[T] =
    loader.createInstanceAs[T](typeName)

  def getAllRegistered: List[ClassDescriptor] =
    loader.getAllRegistered.toList

  def getSubTypes(superType: String): List[ClassDescriptor] =
    loader.getSubTypes(superType)
}

object PackageClassLoader {

  private val packageLoaders: mutable.Map[String, PackageClassLoader] = mutable.Map.empty

  def apply(packageName: String): PackageClassLoader = {
    packageLoaders.getOrElseUpdate(
      packageName,
      new PackageClassLoader(packageName, Reflect.classLoader)
    )
  }

  def domains: PackageClassLoader = apply("app.domain")
  def components: PackageClassLoader = apply("app.components")
  def pages: PackageClassLoader = apply("app.pages")
  def services: PackageClassLoader = apply("app.services")
}
