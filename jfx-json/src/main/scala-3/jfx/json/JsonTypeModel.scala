package jfx.json

import reflect.{
  ArrayTypeDescriptor,
  ClassDescriptor,
  ParameterizedTypeDescriptor,
  TypeDescriptor,
  TypeVariableDescriptor
}

private[json] object JsonTypeModel {

  private val PrimitiveTypes = Set(
    "scala.Predef.String",
    "java.lang.String",
    "scala.Boolean",
    "boolean",
    "scala.Int",
    "int",
    "scala.Double",
    "double",
    "scala.Float",
    "float",
    "scala.Long",
    "long",
    "scala.Short",
    "short",
    "scala.Byte",
    "byte",
    "scala.Char",
    "char",
    "java.util.UUID"
  )

  private val MapTypes = Set(
    "scala.collection.immutable.Map",
    "scala.collection.Map",
    "scala.collection.immutable.ListMap"
  )

  private val CollectionTypes = Set(
    "scala.Array",
    "scala.collection.immutable.List",
    "scala.collection.immutable.Seq",
    "scala.collection.Seq",
    "scala.collection.immutable.Set",
    "scala.collection.Set",
    "scala.scalajs.js.Array"
  )

  private val RawJsonTypes = Set(
    "scala.scalajs.js.Any",
    "scala.Any",
    "scala.scalajs.js.Object"
  )

  def rawTypeName(descriptor: TypeDescriptor): String =
    descriptor match {
      case parameterized: ParameterizedTypeDescriptor => parameterized.rawType.typeName
      case classDescriptor: ClassDescriptor           => classDescriptor.typeName
      case _                                          => descriptor.typeName
    }

  def isPrimitive(descriptor: TypeDescriptor): Boolean =
    PrimitiveTypes.contains(descriptor.typeName)

  def isRawJson(descriptor: TypeDescriptor): Boolean =
    RawJsonTypes.contains(rawTypeName(descriptor))

  def isProperty(descriptor: TypeDescriptor): Boolean =
    rawTypeName(descriptor) == "jfx.core.state.Property"

  def isListProperty(descriptor: TypeDescriptor): Boolean =
    rawTypeName(descriptor) == "jfx.core.state.ListProperty"

  def isOption(descriptor: TypeDescriptor): Boolean =
    rawTypeName(descriptor) == "scala.Option"

  def isMap(descriptor: TypeDescriptor): Boolean =
    MapTypes.contains(rawTypeName(descriptor))

  def isCollection(descriptor: TypeDescriptor): Boolean =
    CollectionTypes.contains(rawTypeName(descriptor))

  def isJsArray(descriptor: TypeDescriptor): Boolean =
    rawTypeName(descriptor) == "scala.scalajs.js.Array"

  def firstTypeArgument(descriptor: TypeDescriptor): TypeDescriptor =
    descriptor match {
      case parameterized: ParameterizedTypeDescriptor if parameterized.typeArguments.nonEmpty =>
        parameterized.typeArguments.head
      case array: ArrayTypeDescriptor =>
        array.componentType
      case _ =>
        throw new IllegalArgumentException(s"Missing type argument for ${descriptor.typeName}")
    }

  def secondTypeArgument(descriptor: TypeDescriptor): TypeDescriptor =
    descriptor match {
      case parameterized: ParameterizedTypeDescriptor if parameterized.typeArguments.length >= 2 =>
        parameterized.typeArguments(1)
      case _ =>
        throw new IllegalArgumentException(
          s"Missing second type argument for ${descriptor.typeName}"
        )
    }

  def typeBindings(descriptor: TypeDescriptor): Map[String, TypeDescriptor] =
    descriptor match {
      case parameterized: ParameterizedTypeDescriptor =>
        typeBindings(parameterized, parameterized.rawType)
      case _ =>
        Map.empty
    }

  def typeBindings(
      descriptor: TypeDescriptor,
      rawDescriptor: ClassDescriptor
  ): Map[String, TypeDescriptor] =
    descriptor match {
      case parameterized: ParameterizedTypeDescriptor =>
        rawDescriptor.typeParameters
          .collect { case variable: TypeVariableDescriptor => variable.name }
          .zip(parameterized.typeArguments)
          .toMap
      case _ =>
        Map.empty
    }

  def substitute(
      bindings: Map[String, TypeDescriptor],
      descriptor: TypeDescriptor
  ): TypeDescriptor =
    descriptor match {
      case variable: TypeVariableDescriptor =>
        bindings.getOrElse(variable.name, variable)
      case parameterized: ParameterizedTypeDescriptor =>
        parameterized.copy(
          typeArguments =
            parameterized.typeArguments.map(argument => substitute(bindings, argument))
        )
      case array: ArrayTypeDescriptor =>
        array.copy(componentType = substitute(bindings, array.componentType))
      case other =>
        other
    }

  def resolveTypeVariable(
      bindings: Map[String, TypeDescriptor],
      variable: TypeVariableDescriptor
  ): TypeDescriptor =
    bindings.getOrElse(variable.name, variable)
}
