package jfx.json

import reflect.ClassDescriptor
import reflect.macros.ReflectMacros

import scala.reflect.ClassTag

final class JsonSchema[T] private[json] (
    val descriptor: ClassDescriptor,
    private[json] val dependencies: Vector[JsonSchema[?]],
    private[json] val subtypes: Vector[JsonSchema[?]]
) {

  def withDependencies(schemas: JsonSchema[?]*): JsonSchema[T] =
    new JsonSchema(
      descriptor,
      (dependencies ++ schemas).distinctBy(_.descriptor.typeName),
      subtypes
    )
}

object JsonSchema {

  def fromDescriptor[T](
      descriptor: ClassDescriptor,
      dependencies: Seq[JsonSchema[?]] = Seq.empty,
      subtypes: Seq[JsonSchema[?]] = Seq.empty
  ): JsonSchema[T] =
    new JsonSchema(descriptor, dependencies.toVector, subtypes.toVector)

  inline def apply[T](inline factory: () => T)(using classTag: ClassTag[T]): JsonSchema[T] = {
    val descriptor = ReflectMacros.reflectWithAccessors[T]
    descriptor.bindRuntimeClass(classTag.runtimeClass)
    descriptor.bindFactory(factory)
    fromDescriptor(descriptor)
  }

  inline def abstractType[T](
      subtypes: JsonSchema[? <: T]*
  )(using classTag: ClassTag[T]): JsonSchema[T] = {
    val descriptor = ReflectMacros.reflectWithAccessors[T]
    descriptor.bindRuntimeClass(classTag.runtimeClass)
    fromDescriptor(descriptor, dependencies = subtypes, subtypes = subtypes)
  }
}
