package jfx.json

import jfx.core.state.{ListProperty, Property}
import reflect.{Annotation, ClassDescriptor, ParameterizedTypeDescriptor, PropertyAccessor, PropertyDescriptor, TypeDescriptor, TypeVariableDescriptor}
import reflect.macros.ReflectMacros.reflectType

import java.util.UUID
import scala.collection.immutable.{ListMap, Map as ImmutableMap}
import scala.scalajs.js
import scala.scalajs.js.Dynamic
import scala.scalajs.js.JSConverters.*

class JsonMapper {

  inline def serialize[M](model: M): Dynamic =
    JsonMapper.serialize(model, reflectType[M])

  def serialize[M](model: M, meta: TypeDescriptor): Dynamic =
    JsonMapper.serialize(model, meta)

  inline def deserialize[M](json: Dynamic): M =
    JsonMapper.deserialize(json, reflectType[M])

  def deserialize[M](json: Dynamic, meta: TypeDescriptor): M =
    JsonMapper.deserialize(json, meta)

  def deserializeArray[M](json: js.Array[js.Dynamic], meta: TypeDescriptor): Seq[M] =
    JsonMapper.deserializeArray(json, meta)
}

object JsonMapper {

  private val JsonTypeAnnotation = "jfx.json.JsonType"
  private val JsonNameAnnotation = "jfx.json.JsonName"
  private val JsonIgnoreAnnotation = "jfx.json.JsonIgnore"
  private val JsonIgnoreWriteAnnotation = "jfx.json.JsonIgnoreWrite"
  private val TypeField = "@type"
  private val IdField = "id"
  inline def serialize[M](model: M): Dynamic =
    serialize(model, reflectType[M])

  def serialize[M](model: M, meta: TypeDescriptor): Dynamic =
    serializeValue(model, JsonContext.root(meta)).asInstanceOf[Dynamic]

  inline def deserialize[M](json: Dynamic): M =
    deserialize(json, reflectType[M])

  def deserialize[M](json: Dynamic, meta: TypeDescriptor): M =
    deserializeValue(json, JsonContext.root(meta)).asInstanceOf[M]

  def deserializeArray[M](json: js.Array[js.Dynamic], meta: TypeDescriptor): Seq[M] =
    if (json == null || js.isUndefined(json)) {
      Seq.empty
    } else {
      json.toSeq.map(value => deserializeValue(value, JsonContext.root(meta)).asInstanceOf[M])
    }

  private final case class JsonContext(expectedType: TypeDescriptor, bindings: Map[String, TypeDescriptor]) {
    def resolve(tpe: TypeDescriptor): TypeDescriptor =
      substitute(bindings, tpe)
  }

  private object JsonContext {
    def root(meta: TypeDescriptor): JsonContext =
      JsonContext(meta, typeBindings(meta))
  }

  private def serializeValue(value: Any, context: JsonContext): js.Any = {
    if (value == null) {
      null
    } else {
      val expectedType = context.resolve(context.expectedType)
      expectedType match {
        case descriptor if isRawJsonType(descriptor) =>
          value.asInstanceOf[js.Any]
        case descriptor if isPropertyType(descriptor) =>
          serializePropertyValue(value, context)
        case descriptor if isListPropertyType(descriptor) =>
          serializeListPropertyValue(value, context)
        case descriptor if isOptionType(descriptor) =>
          serializeOptionValue(value, context)
        case descriptor if isMapType(descriptor) =>
          serializeMapValue(value, context)
        case descriptor if isCollectionType(descriptor) =>
          serializeCollectionValue(value, context)
        case descriptor if isPrimitiveType(descriptor.typeName) =>
          serializePrimitive(value, descriptor.typeName)
        case descriptor: ParameterizedTypeDescriptor =>
          serializeObject(value, descriptor.rawType, context.copy(expectedType = descriptor))
        case descriptor: ClassDescriptor if isPrimitiveType(descriptor.typeName) =>
          serializePrimitive(value, descriptor.typeName)
        case descriptor: ClassDescriptor =>
          serializeObject(value, descriptor, context)
        case _ =>
          throw new IllegalArgumentException(s"Unsupported type descriptor for serialization: ${expectedType.typeName}")
      }
    }
  }

  private def deserializeValue(value: js.Any, context: JsonContext): Any = {
    val expectedType = context.resolve(context.expectedType)
    if (value == null || js.isUndefined(value)) {
      if (isPropertyType(expectedType)) {
        deserializePropertyValue(value, context)
      } else if (isOptionType(expectedType)) {
        None
      } else {
        null
      }
    } else {
      expectedType match {
        case descriptor if isRawJsonType(descriptor) =>
          value
        case descriptor if isPropertyType(descriptor) =>
          deserializePropertyValue(value, context)
        case descriptor if isListPropertyType(descriptor) =>
          deserializeListPropertyValue(value, context)
        case descriptor if isOptionType(descriptor) =>
          deserializeOptionValue(value, context)
        case descriptor if isMapType(descriptor) =>
          deserializeMapValue(value, context)
        case descriptor if isCollectionType(descriptor) =>
          deserializeCollectionValue(value, context)
        case descriptor if isPrimitiveType(descriptor.typeName) =>
          deserializePrimitive(value, descriptor.typeName)
        case descriptor: ParameterizedTypeDescriptor =>
          deserializeObjectValue(value, descriptor.rawType, context)
        case descriptor: ClassDescriptor if isPrimitiveType(descriptor.typeName) =>
          deserializePrimitive(value, descriptor.typeName)
        case descriptor: ClassDescriptor =>
          deserializeObjectValue(value, descriptor, context)
        case descriptor: TypeVariableDescriptor =>
          deserializeValue(value, context.copy(expectedType = resolveTypeVariable(context.bindings, descriptor)))
        case _ =>
          throw new IllegalArgumentException(s"Unsupported type descriptor for deserialization: ${expectedType.typeName}")
      }
    }
  }

  private def serializeObject(model: Any, declaredDescriptor: ClassDescriptor, parentContext: JsonContext): js.Dynamic = {
    val runtimeDescriptor = serializationDescriptorForValue(model, declaredDescriptor)
    val context = JsonContext(declaredDescriptor, typeBindings(parentContext.resolve(parentContext.expectedType), declaredDescriptor))
    val obj = js.Dictionary.empty[js.Any]

    jsonTypeValue(runtimeDescriptor).foreach(typeName => obj(TypeField) = typeName)

    val properties = writableSerializableProperties(runtimeDescriptor)
    if (isInlineMapShape(runtimeDescriptor, properties)) {
      val property = properties.head
      val accessor = propertyAccessor(runtimeDescriptor, property)
      val propertyValue = accessor.get(model.asInstanceOf[Any])
      val fieldContext = childContext(context, property.propertyType)
      serializeMapEntries(propertyValue, fieldContext).foreach { case (key, mappedValue) =>
        obj(key) = mappedValue
      }
    } else {
      properties.foreach { property =>
        val accessor = propertyAccessor(runtimeDescriptor, property)
        val propertyValue = accessor.get(model.asInstanceOf[Any])
        val fieldContext = childContext(context, property.propertyType)
        obj(jsonFieldName(property)) = serializeValue(propertyValue, fieldContext)
      }
    }

    obj.asInstanceOf[js.Dynamic]
  }

  private def deserializeObjectValue(value: js.Any, declaredDescriptor: ClassDescriptor, parentContext: JsonContext): Any = {
    val resolvedDescriptor = resolvePolymorphicDescriptor(declaredDescriptor, value).getOrElse(declaredDescriptor.resolved)
    val bindings = typeBindings(parentContext.resolve(parentContext.expectedType), resolvedDescriptor)
    val context = JsonContext(resolvedDescriptor, bindings)
    val instance = resolvedDescriptor.requireCreateInstance()
    val properties = readableSerializableProperties(resolvedDescriptor)
    val jsonObject = asDictionary(value)

    if (isInlineMapShape(resolvedDescriptor, properties)) {
      val property = properties.head
      val fieldContext = childContext(context, property.propertyType)
      val mapValue = deserializeInlineMap(jsonObject, fieldContext)
      assignProperty(instance, resolvedDescriptor, property, mapValue)
    } else {
      properties.foreach { property =>
        val fieldName = jsonFieldName(property)
        if (jsonObject.contains(fieldName)) {
          val rawValue = jsonObject(fieldName)
          val fieldContext = childContext(context, property.propertyType)
          val mappedValue = deserializeValue(rawValue, fieldContext)
          assignProperty(instance, resolvedDescriptor, property, mappedValue)
        }
      }
    }

    instance
  }

  private def serializePropertyValue(value: Any, context: JsonContext): js.Any = {
    val property = value.asInstanceOf[Property[Any]]
    val innerType = propertyElementType(context.expectedType)
    serializeValue(property.get, childContext(context, innerType))
  }

  private def serializeListPropertyValue(value: Any, context: JsonContext): js.Any = {
    val property = value.asInstanceOf[ListProperty[Any]]
    val elementType = listElementType(context.expectedType)
    js.Array(property.get.toSeq.map(item => serializeValue(item, childContext(context, elementType)))*)
  }

  private def serializeOptionValue(value: Any, context: JsonContext): js.Any =
    value.asInstanceOf[Option[Any]] match {
      case Some(inner) =>
        val innerType = firstTypeArgument(context.expectedType)
        serializeValue(inner, childContext(context, innerType))
      case None =>
        null
    }

  private def serializeMapValue(value: Any, context: JsonContext): js.Any =
    js.Dictionary(serializeMapEntries(value, context)*).asInstanceOf[js.Any]

  private def serializeMapEntries(value: Any, context: JsonContext): Seq[(String, js.Any)] = {
    val valueType = secondTypeArgument(context.expectedType)
    value match {
      case entries: scala.collection.Map[?, ?] =>
        entries.toSeq.map { case (key, entryValue) =>
          key.toString -> serializeValue(entryValue, childContext(context, valueType))
        }
      case other =>
        throw new IllegalArgumentException(s"Expected map for ${context.expectedType.typeName}, got ${other.getClass.getName}")
    }
  }

  private def serializeCollectionValue(value: Any, context: JsonContext): js.Any = {
    val elementType = firstTypeArgument(context.expectedType)
    val values =
      value match {
        case array: js.Array[?] => array.toSeq
        case array: Array[?] => array.toSeq
        case iterable: Iterable[?] => iterable.toSeq
        case other =>
          throw new IllegalArgumentException(s"Expected collection for ${context.expectedType.typeName}, got ${other.getClass.getName}")
      }
    js.Array(values.map(item => serializeValue(item, childContext(context, elementType)))*)
  }

  private def serializePrimitive(value: Any, typeName: String): js.Any =
    typeName match {
      case "java.util.UUID" =>
        value.asInstanceOf[UUID].toString
      case "scala.Char" | "char" =>
        value.toString
      case _ =>
        value.asInstanceOf[js.Any]
    }

  private def deserializePropertyValue(value: js.Any, context: JsonContext): Any = {
    val innerType = propertyElementType(context.expectedType)
    deserializeValue(value, childContext(context, innerType))
  }

  private def deserializeListPropertyValue(value: js.Any, context: JsonContext): Any =
    deserializeCollectionValue(value, context)

  private def deserializeOptionValue(value: js.Any, context: JsonContext): Any =
    if (value == null || js.isUndefined(value)) {
      None
    } else {
      val innerType = firstTypeArgument(context.expectedType)
      Some(deserializeValue(value, childContext(context, innerType)))
    }

  private def deserializeMapValue(value: js.Any, context: JsonContext): Any = {
    val elementType = secondTypeArgument(context.expectedType)
    val entries = asDictionary(value).toSeq.map { case (key, entryValue) =>
      key -> deserializeValue(entryValue, childContext(context, elementType))
    }
    collectionFactory(context.expectedType, entries)
  }

  private def deserializeInlineMap(json: js.Dictionary[js.Any], context: JsonContext): Any = {
    val reserved = Set(TypeField)
    val entries = json.toSeq.collect {
      case (key, rawValue) if !reserved.contains(key) =>
        key -> deserializeValue(rawValue, childContext(context, secondTypeArgument(context.expectedType)))
    }
    collectionFactory(context.expectedType, entries)
  }

  private def deserializeCollectionValue(value: js.Any, context: JsonContext): Any = {
    val elementType = firstTypeArgument(context.expectedType)
    val items = asJsArray(value).map(item => deserializeValue(item, childContext(context, elementType))).toSeq
    val rawTypeName = rawTypeNameOf(context.expectedType)

    if (isListPropertyType(context.expectedType)) {
      items
    } else if (isJsArrayType(context.expectedType)) {
      js.Array(items*)
    } else if (rawTypeName == "scala.Array") {
      items.toArray
    } else if (rawTypeName == "scala.collection.immutable.List") {
      items.toList
    } else if (rawTypeName == "scala.collection.immutable.Seq" || rawTypeName == "scala.collection.Seq") {
      items.toSeq
    } else if (rawTypeName == "scala.collection.immutable.Set" || rawTypeName == "scala.collection.Set") {
      items.toSet
    } else {
      throw new IllegalArgumentException(s"Unsupported collection type for deserialization: $rawTypeName")
    }
  }

  private def deserializePrimitive(value: js.Any, typeName: String): Any =
    typeName match {
      case "scala.Int" | "int" =>
        value.asInstanceOf[Double].toInt
      case "scala.Double" | "double" =>
        value.asInstanceOf[Double]
      case "scala.Float" | "float" =>
        value.asInstanceOf[Double].toFloat
      case "scala.Long" | "long" =>
        value.asInstanceOf[Double].toLong
      case "scala.Short" | "short" =>
        value.asInstanceOf[Double].toShort
      case "scala.Byte" | "byte" =>
        value.asInstanceOf[Double].toByte
      case "scala.Boolean" | "boolean" =>
        value.asInstanceOf[Boolean]
      case "scala.Char" | "char" =>
        value.toString.headOption.getOrElse('\u0000')
      case "java.util.UUID" =>
        UUID.fromString(extractUuidString(value.toString))
      case _ =>
        value
    }

  private def assignProperty(instance: Any, owner: ClassDescriptor, property: PropertyDescriptor, value: Any): Unit = {
    val accessor = propertyAccessor(owner, property)
    val currentValue = accessor.get(instance)

    currentValue match {
      case propertyValue: Property[?] =>
        propertyValue.asInstanceOf[Property[Any]].set(value)
      case propertyValue: ListProperty[?] =>
        val values =
          value match {
            case array: js.Array[?] => array.toSeq
            case seq: Seq[?] => seq
            case iterable: Iterable[?] => iterable.toSeq
            case null => Seq.empty
            case other =>
              throw new IllegalArgumentException(s"Expected sequence value for list property ${owner.typeName}.${property.name}, got ${other.getClass.getName}")
          }
        propertyValue.asInstanceOf[ListProperty[Any]].setAll(values.asInstanceOf[Seq[Any]])
      case _ =>
        if (accessor.hasSetter) {
          accessor.set(instance, value)
        } else if (property.isWriteable) {
          instance.asInstanceOf[js.Dynamic].updateDynamic(property.name)(value.asInstanceOf[js.Any])
        } else {
          throw new IllegalArgumentException(s"Property ${owner.typeName}.${property.name} is not writeable")
        }
    }
  }

  private def resolvePolymorphicDescriptor(declaredDescriptor: ClassDescriptor, value: js.Any): Option[ClassDescriptor] = {
    val jsonObject = asDictionary(value)
    val jsonType = jsonObject.get(TypeField).map(_.toString)
    val jsonId = jsonObject.get(IdField).map(_.toString)
    val candidates = subtypeCandidates(declaredDescriptor)
    val descriptorById = jsonId.flatMap(id => candidates.find(matchesJsonType(_, id)))
    val descriptorByType =
      jsonType.flatMap { typeName =>
        candidates.find(matchesJsonType(_, typeName))
          .orElse {
            ClassDescriptor.maybeForName(typeName)
              .filter(descriptor => descriptor.typeName == declaredDescriptor.typeName || isAssignableTo(descriptor, declaredDescriptor.typeName))
          }
      }

    if (declaredDescriptor.isAbstract) {
      descriptorById
        .orElse(descriptorByType)
        .orElse {
          jsonType.map(typeName => throw new IllegalArgumentException(s"Unknown @type '$typeName' for ${declaredDescriptor.typeName}"))
        }
        .orElse(throw new IllegalArgumentException(s"Missing @type for abstract type ${declaredDescriptor.typeName}"))
    } else {
      jsonType match {
        case Some(typeName) =>
          descriptorByType
          .orElse {
            throw new IllegalArgumentException(s"Unknown @type '$typeName' for ${declaredDescriptor.typeName}")
          }
        case None =>
          Some(declaredDescriptor.resolved)
      }
    }
  }

  private def serializationDescriptorForValue(value: Any, declaredDescriptor: ClassDescriptor): ClassDescriptor = {
    val fullDeclared = declaredDescriptor.resolved
    if (!fullDeclared.isAbstract) {
      fullDeclared
    } else {
      runtimeDescriptorForValue(value, fullDeclared)
    }
  }

  private def runtimeDescriptorForValue(value: Any, fallback: ClassDescriptor): ClassDescriptor = {
    val runtimeNames = candidateRuntimeNames(value)
    val registryMatch =
      runtimeNames.iterator
        .flatMap(name => ClassDescriptor.maybeForName(name))
        .toSeq
        .headOption

    registryMatch
      .orElse {
        subtypeCandidates(fallback).find { candidate =>
          runtimeNames.contains(candidate.typeName) || runtimeNames.contains(candidate.simpleName)
        }
      }
      .orElse(Option.when(!fallback.isAbstract)(fallback))
      .getOrElse(throw new IllegalArgumentException(s"Cannot resolve runtime descriptor for value of ${runtimeNames.headOption.getOrElse(value.getClass.getName)} as ${fallback.typeName}"))
  }

  private def subtypeCandidates(descriptor: ClassDescriptor): List[ClassDescriptor] =
    (ClassDescriptor.maybeResolve(descriptor).toList ++
      ClassDescriptor.all.filter(candidate => isAssignableTo(candidate, descriptor.typeName)).toList)
      .distinctBy(_.typeName)

  private def matchesJsonType(descriptor: ClassDescriptor, jsonType: String): Boolean = {
    val descriptorNames = Set(
      descriptor.typeName,
      descriptor.simpleName
    ) ++ jsonTypeValue(descriptor).toSet

    val jsonNames = jsonTypeTokens(jsonType)

    descriptorNames.exists(name => jsonNames.contains(name) || jsonNames.contains(name.trim))
  }

  private def jsonTypeTokens(value: String): Set[String] = {
    if (value == null || value.isBlank) {
      Set.empty
    } else {
      val normalized = value.trim
      val localName =
        normalized
          .split("[/#:]")
          .iterator
          .filter(_.nonEmpty)
          .toSeq
          .lastOption
          .getOrElse(normalized)

      Set(normalized, localName)
    }
  }

  private def jsonTypeValue(descriptor: ClassDescriptor): Option[String] =
    annotationValue(descriptor.annotations, JsonTypeAnnotation)

  private def isAssignableTo(descriptor: ClassDescriptor, superTypeName: String): Boolean =
    descriptor.typeName == superTypeName || descriptor.isAssignableTo(superTypeName)

  private def candidateRuntimeNames(value: Any): Vector[String] = {
    val javaClass = value.getClass
    val dynamic = value.asInstanceOf[js.Dynamic]
    val constructorName =
      if (js.isUndefined(dynamic.selectDynamic("constructor")) || js.isUndefined(dynamic.selectDynamic("constructor").selectDynamic("name"))) None
      else Option(dynamic.selectDynamic("constructor").selectDynamic("name").toString)

    Vector(
      Option(javaClass.getName),
      Option(javaClass.getSimpleName),
      constructorName
    ).flatten.distinct
  }

  private def writableSerializableProperties(descriptor: ClassDescriptor): Array[PropertyDescriptor] =
    descriptor.resolved.properties
      .filterNot(property => property.hasAnnotation(JsonIgnoreAnnotation) || property.hasAnnotation(JsonIgnoreWriteAnnotation))
      .filter(isSerializableProperty)

  private def readableSerializableProperties(descriptor: ClassDescriptor): Array[PropertyDescriptor] =
    descriptor.resolved.properties
      .filterNot(_.hasAnnotation(JsonIgnoreAnnotation))
      .filter(isSerializableProperty)

  private def isSerializableProperty(property: PropertyDescriptor): Boolean =
    property.isPublic &&
      property.name.nonEmpty &&
      !property.name.contains("$") &&
      property.name.forall(ch => ch.isLetterOrDigit || ch == '_')

  private def jsonFieldName(property: PropertyDescriptor): String =
    annotationValue(property.annotations, JsonNameAnnotation).getOrElse(property.name)

  private def annotationValue(annotations: Array[Annotation], annotationClassName: String): Option[String] =
    annotations.find(_.annotationClassName == annotationClassName).flatMap(_.parameters.get("value")).map(_.toString)

  private def propertyAccessor(owner: ClassDescriptor, property: PropertyDescriptor): PropertyAccessor[Any, Any] =
    owner.requirePropertyAccessor(property.name).asInstanceOf[PropertyAccessor[Any, Any]]

  private def childContext(parent: JsonContext, childType: TypeDescriptor): JsonContext = {
    val resolved = parent.resolve(childType)
    resolved match {
      case parameterized: ParameterizedTypeDescriptor =>
        val bindings = typeBindings(parameterized, parameterized.rawType)
        JsonContext(resolved, parent.bindings ++ bindings)
      case _ =>
        JsonContext(resolved, parent.bindings)
    }
  }

  private def typeBindings(descriptor: TypeDescriptor): Map[String, TypeDescriptor] =
    descriptor match {
      case parameterized: ParameterizedTypeDescriptor =>
        typeBindings(parameterized, rawClassDescriptor(parameterized))
      case _ =>
        Map.empty
    }

  private def typeBindings(descriptor: TypeDescriptor, rawDescriptor: ClassDescriptor): Map[String, TypeDescriptor] =
    descriptor match {
      case parameterized: ParameterizedTypeDescriptor =>
        rawDescriptor.typeParameters.collect { case variable: TypeVariableDescriptor => variable.name }
          .zip(parameterized.typeArguments)
          .toMap
      case _ =>
        Map.empty
    }

  private def substitute(bindings: Map[String, TypeDescriptor], descriptor: TypeDescriptor): TypeDescriptor =
    descriptor match {
      case variable: TypeVariableDescriptor =>
        resolveTypeVariable(bindings, variable)
      case parameterized: ParameterizedTypeDescriptor =>
        parameterized.copy(typeArguments = parameterized.typeArguments.map(arg => substitute(bindings, arg)))
      case other =>
        other
    }

  private def resolveTypeVariable(bindings: Map[String, TypeDescriptor], descriptor: TypeVariableDescriptor): TypeDescriptor =
    bindings.getOrElse(descriptor.name, descriptor)

  private def rawClassDescriptor(descriptor: TypeDescriptor): ClassDescriptor =
    descriptor match {
      case parameterized: ParameterizedTypeDescriptor =>
        parameterized.rawType
      case classDescriptor: ClassDescriptor =>
        classDescriptor.resolved
      case variable: TypeVariableDescriptor =>
        variable.bounds.iterator.flatMap(ClassDescriptor.maybeForName).toSeq.headOption.getOrElse(
          throw new IllegalArgumentException(s"Cannot resolve type variable ${variable.name}")
        )
      case other =>
        throw new IllegalArgumentException(s"Unsupported descriptor ${other.typeName}")
    }

  private def propertyElementType(descriptor: TypeDescriptor): TypeDescriptor =
    firstTypeArgument(descriptor)

  private def listElementType(descriptor: TypeDescriptor): TypeDescriptor =
    firstTypeArgument(descriptor)

  private def firstTypeArgument(descriptor: TypeDescriptor): TypeDescriptor =
    descriptor match {
      case parameterized: ParameterizedTypeDescriptor if parameterized.typeArguments.nonEmpty =>
        parameterized.typeArguments.head
      case _ =>
        throw new IllegalArgumentException(s"Missing type argument for ${descriptor.typeName}")
    }

  private def secondTypeArgument(descriptor: TypeDescriptor): TypeDescriptor =
    descriptor match {
      case parameterized: ParameterizedTypeDescriptor if parameterized.typeArguments.length >= 2 =>
        parameterized.typeArguments(1)
      case _ =>
        throw new IllegalArgumentException(s"Missing second type argument for ${descriptor.typeName}")
    }

  private def rawTypeNameOf(descriptor: TypeDescriptor): String =
    descriptor match {
      case parameterized: ParameterizedTypeDescriptor => parameterized.rawType.typeName
      case classDescriptor: ClassDescriptor => classDescriptor.typeName
      case _ => descriptor.typeName
    }

  private def collectionFactory(descriptor: TypeDescriptor, entries: Seq[(String, Any)]): scala.collection.Map[String, Any] = {
    val rawTypeName = rawTypeNameOf(descriptor)
    if (rawTypeName == "scala.collection.immutable.ListMap") {
      ListMap(entries*)
    } else if (rawTypeName == "scala.collection.immutable.Map" || rawTypeName == "scala.collection.Map") {
      ImmutableMap(entries*)
    } else {
      throw new IllegalArgumentException(s"Unsupported map type for deserialization: $rawTypeName")
    }
  }

  private def isInlineMapShape(descriptor: ClassDescriptor, properties: Array[PropertyDescriptor]): Boolean = {
    val dataProperties = properties.filter(isInlineShapeProperty)
    dataProperties.length == 1 && isMapType(dataProperties.head.propertyType)
  }

  private def isInlineShapeProperty(property: PropertyDescriptor): Boolean =
    property.isWriteable || property.accessor.exists(_.hasSetter)

  private def isPropertyType(descriptor: TypeDescriptor): Boolean =
    rawTypeNameOf(descriptor) == "jfx.core.state.Property"

  private def isListPropertyType(descriptor: TypeDescriptor): Boolean =
    rawTypeNameOf(descriptor) == "jfx.core.state.ListProperty"

  private def isOptionType(descriptor: TypeDescriptor): Boolean =
    rawTypeNameOf(descriptor) == "scala.Option"

  private def isMapType(descriptor: TypeDescriptor): Boolean = {
    val rawTypeName = rawTypeNameOf(descriptor)
    rawTypeName == "scala.collection.immutable.Map" ||
    rawTypeName == "scala.collection.Map" ||
    rawTypeName == "scala.collection.immutable.ListMap"
  }

  private def isCollectionType(descriptor: TypeDescriptor): Boolean = {
    val rawTypeName = rawTypeNameOf(descriptor)
    isJsArrayType(descriptor) ||
    rawTypeName == "scala.Array" ||
    rawTypeName == "scala.collection.immutable.List" ||
    rawTypeName == "scala.collection.immutable.Seq" ||
    rawTypeName == "scala.collection.Seq" ||
    rawTypeName == "scala.collection.immutable.Set" ||
    rawTypeName == "scala.collection.Set"
  }

  private def isJsArrayType(descriptor: TypeDescriptor): Boolean =
    rawTypeNameOf(descriptor) == "scala.scalajs.js.Array"

  private def isPrimitiveType(typeName: String): Boolean =
    typeName == "scala.Predef.String" ||
    typeName == "java.lang.String" ||
    typeName == "scala.Boolean" ||
    typeName == "boolean" ||
    typeName == "scala.Int" ||
    typeName == "int" ||
    typeName == "scala.Double" ||
    typeName == "double" ||
    typeName == "scala.Float" ||
    typeName == "float" ||
    typeName == "scala.Long" ||
    typeName == "long" ||
    typeName == "scala.Short" ||
    typeName == "short" ||
    typeName == "scala.Byte" ||
    typeName == "byte" ||
    typeName == "scala.Char" ||
    typeName == "char" ||
    typeName == "java.util.UUID"

  private def isRawJsonType(descriptor: TypeDescriptor): Boolean = {
    val typeName = rawTypeNameOf(descriptor)
    typeName == "scala.scalajs.js.Any" ||
    typeName == "scala.Any" ||
    typeName == "scala.scalajs.js.Object"
  }

  private def asDictionary(value: js.Any): js.Dictionary[js.Any] =
    if (value != null && !js.isUndefined(value) && !js.Array.isArray(value) && js.typeOf(value) == "object") {
      value.asInstanceOf[js.Dictionary[js.Any]]
    } else {
      throw new IllegalArgumentException(s"Expected JSON object, got ${js.typeOf(value)}")
    }

  private def asJsArray(value: js.Any): js.Array[js.Any] =
    if (js.Array.isArray(value)) value.asInstanceOf[js.Array[js.Any]]
    else throw new IllegalArgumentException(s"Expected JSON array, got ${js.typeOf(value)}")

  private def extractUuidString(raw: String): String = {
    val value = Option(raw).getOrElse("").trim
    if (value.contains("/")) value.split('/').lastOption.getOrElse(value)
    else value
  }
}
