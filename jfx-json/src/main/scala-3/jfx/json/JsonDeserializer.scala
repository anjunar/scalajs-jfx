package jfx.json

import jfx.core.state.{ListProperty, Property}
import reflect.{
  ClassDescriptor,
  ParameterizedTypeDescriptor,
  PropertyDescriptor,
  TypeVariableDescriptor
}

import scala.collection.immutable.{ListMap, Map as ImmutableMap}
import scala.reflect.ClassTag
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

private[json] object JsonDeserializer {

  def deserialize(value: js.Any, context: JsonMappingContext): Any = {
    val expectedType = context.resolvedType

    if (value == null || js.isUndefined(value)) {
      if (JsonTypeModel.isProperty(expectedType)) deserializeProperty(value, context)
      else if (JsonTypeModel.isOption(expectedType)) None
      else null
    } else {
      expectedType match {
        case descriptor if JsonTypeModel.isRawJson(descriptor) =>
          value
        case descriptor if JsonTypeModel.isProperty(descriptor) =>
          deserializeProperty(value, context)
        case descriptor if JsonTypeModel.isListProperty(descriptor) =>
          deserializeListProperty(value, context)
        case descriptor if JsonTypeModel.isOption(descriptor) =>
          deserializeOption(value, context)
        case descriptor if JsonTypeModel.isMap(descriptor) =>
          deserializeMap(value, context)
        case descriptor if JsonTypeModel.isCollection(descriptor) =>
          deserializeCollection(value, context)
        case descriptor if JsonTypeModel.isPrimitive(descriptor) =>
          JsonValueCodec.deserializePrimitive(value, descriptor.typeName)
        case parameterized: ParameterizedTypeDescriptor =>
          deserializeObject(value, parameterized.rawType, context)
        case descriptor: ClassDescriptor =>
          deserializeObject(value, descriptor, context)
        case variable: TypeVariableDescriptor =>
          val resolved = JsonTypeModel.resolveTypeVariable(context.bindings, variable)
          if (resolved == variable) {
            throw new IllegalArgumentException(s"Cannot resolve type variable ${variable.name}")
          }
          deserialize(value, context.copy(expectedType = resolved))
        case descriptor =>
          throw new IllegalArgumentException(
            s"Unsupported type descriptor for deserialization: ${descriptor.typeName}"
          )
      }
    }
  }

  private def deserializeObject(
      value: js.Any,
      declaredDescriptor: ClassDescriptor,
      parentContext: JsonMappingContext
  ): Any = {
    val jsonObject = JsonValueCodec.asObject(value)
    val descriptor =
      JsonMetadata.descriptorForDeserialization(
        declaredDescriptor,
        jsonObject,
        parentContext.schemas
      )
    val context    = objectContext(parentContext, descriptor)
    val instance   = descriptor.requireCreateInstance()
    val properties = JsonMetadata.deserializationProperties(descriptor)

    JsonMetadata.inlineMapProperty(properties) match {
      case Some(property) =>
        val fieldContext = context.child(property.propertyType)
        val mappedValue  = deserializeInlineMap(jsonObject, fieldContext)
        assign(instance, descriptor, property, mappedValue)
      case None =>
        properties.foreach { property =>
          val fieldName = JsonMetadata.fieldName(property)
          if (jsonObject.contains(fieldName)) {
            val fieldContext = context.child(property.propertyType)
            val mappedValue  = deserialize(jsonObject(fieldName), fieldContext)
            assign(instance, descriptor, property, mappedValue)
          }
        }
    }

    instance
  }

  private def deserializeProperty(value: js.Any, context: JsonMappingContext): Any =
    deserialize(
      value,
      context.child(JsonTypeModel.firstTypeArgument(context.resolvedType))
    )

  private def deserializeListProperty(value: js.Any, context: JsonMappingContext): Any = {
    val elementType = JsonTypeModel.firstTypeArgument(context.resolvedType)
    JsonValueCodec
      .asArray(value)
      .map(item => deserialize(item, context.child(elementType)))
      .toSeq
  }

  private def deserializeOption(value: js.Any, context: JsonMappingContext): Any =
    Some(
      deserialize(
        value,
        context.child(JsonTypeModel.firstTypeArgument(context.resolvedType))
      )
    )

  private def deserializeMap(value: js.Any, context: JsonMappingContext): Any = {
    val valueType = JsonTypeModel.secondTypeArgument(context.resolvedType)
    val entries   = JsonValueCodec.asObject(value).toSeq.map { case (key, entryValue) =>
      key -> deserialize(entryValue, context.child(valueType))
    }

    createMap(context.resolvedType, entries)
  }

  private def deserializeInlineMap(
      jsonObject: js.Dictionary[js.Any],
      context: JsonMappingContext
  ): Any = {
    val valueType = JsonTypeModel.secondTypeArgument(context.resolvedType)
    val entries   = jsonObject.toSeq.collect {
      case (key, rawValue) if key != JsonMetadata.TypeField =>
        key -> deserialize(rawValue, context.child(valueType))
    }

    createMap(context.resolvedType, entries)
  }

  private def deserializeCollection(value: js.Any, context: JsonMappingContext): Any = {
    val elementType = JsonTypeModel.firstTypeArgument(context.resolvedType)
    val items       = JsonValueCodec
      .asArray(value)
      .map(item => deserialize(item, context.child(elementType)))
      .toSeq

    JsonTypeModel.rawTypeName(context.resolvedType) match {
      case "scala.scalajs.js.Array" =>
        js.Array(items*)
      case "scala.Array" =>
        createScalaArray(items, elementType, context.schemas)
      case "scala.collection.immutable.List" =>
        items.toList
      case "scala.collection.immutable.Seq" | "scala.collection.Seq" =>
        items
      case "scala.collection.immutable.Set" | "scala.collection.Set" =>
        items.toSet
      case rawTypeName =>
        throw new IllegalArgumentException(
          s"Unsupported collection type for deserialization: $rawTypeName"
        )
    }
  }

  private def assign(
      instance: Any,
      owner: ClassDescriptor,
      property: PropertyDescriptor,
      value: Any
  ): Unit = {
    val accessor     = JsonMetadata.accessor(owner, property)
    val currentValue = accessor.get(instance)

    currentValue match {
      case target: Property[?] =>
        val property = target.asInstanceOf[Property[Any]]
        property.set(value)
        property.setDefault(value)
      case target: ListProperty[?] =>
        val listProperty = target.asInstanceOf[ListProperty[Any]]
        val values       = value match {
          case array: js.Array[?]    => array.toSeq
          case sequence: Seq[?]      => sequence
          case iterable: Iterable[?] => iterable.toSeq
          case null                  => Seq.empty
          case other                 =>
            throw new IllegalArgumentException(
              s"Expected sequence value for list property ${owner.typeName}.${property.name}, got ${other.getClass.getName}"
            )
        }
        listProperty.setAll(values.asInstanceOf[Seq[Any]])
        listProperty.setDefaultValue(values.toJSArray)
      case _ if accessor.hasSetter =>
        accessor.set(instance, value)
      case _ if property.isWriteable =>
        instance
          .asInstanceOf[js.Dynamic]
          .updateDynamic(property.name)(value.asInstanceOf[js.Any])
      case _ =>
        throw new IllegalArgumentException(
          s"Property ${owner.typeName}.${property.name} is not writeable"
        )
    }
  }

  private def createMap(
      descriptor: reflect.TypeDescriptor,
      entries: Seq[(String, Any)]
  ): scala.collection.Map[String, Any] =
    JsonTypeModel.rawTypeName(descriptor) match {
      case "scala.collection.immutable.ListMap" =>
        ListMap(entries*)
      case "scala.collection.immutable.Map" | "scala.collection.Map" =>
        ImmutableMap(entries*)
      case rawTypeName =>
        throw new IllegalArgumentException(
          s"Unsupported map type for deserialization: $rawTypeName"
        )
    }

  private def createScalaArray(
      items: Seq[Any],
      elementType: reflect.TypeDescriptor,
      schemas: JsonSchemaCatalog
  ): Array[?] =
    elementType.typeName match {
      case "scala.Boolean" | "boolean"                => items.map(_.asInstanceOf[Boolean]).toArray
      case "scala.Byte" | "byte"                      => items.map(_.asInstanceOf[Byte]).toArray
      case "scala.Short" | "short"                    => items.map(_.asInstanceOf[Short]).toArray
      case "scala.Int" | "int"                        => items.map(_.asInstanceOf[Int]).toArray
      case "scala.Long" | "long"                      => items.map(_.asInstanceOf[Long]).toArray
      case "scala.Float" | "float"                    => items.map(_.asInstanceOf[Float]).toArray
      case "scala.Double" | "double"                  => items.map(_.asInstanceOf[Double]).toArray
      case "scala.Char" | "char"                      => items.map(_.asInstanceOf[Char]).toArray
      case "scala.Predef.String" | "java.lang.String" =>
        items.map(_.asInstanceOf[String]).toArray
      case "java.util.UUID" =>
        items.map(_.asInstanceOf[java.util.UUID]).toArray
      case _ =>
        val runtimeClass = elementType match {
          case parameterized: ParameterizedTypeDescriptor =>
            schemas.resolve(parameterized.rawType).runtimeClass
          case descriptor: ClassDescriptor =>
            schemas.resolve(descriptor).runtimeClass
          case _ =>
            None
        }

        runtimeClass match {
          case Some(clazz) =>
            items.toArray(using ClassTag(clazz.asInstanceOf[Class[Any]]))
          case None =>
            throw new IllegalArgumentException(
              s"Cannot create Scala array for element type ${elementType.typeName}: no runtime class is registered"
            )
        }
    }

  private def objectContext(
      parent: JsonMappingContext,
      descriptor: ClassDescriptor
  ): JsonMappingContext =
    JsonMappingContext(
      descriptor,
      parent.bindings ++ JsonTypeModel.typeBindings(parent.resolvedType, descriptor),
      parent.schemas
    )
}
