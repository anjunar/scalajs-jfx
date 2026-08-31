package jfx.json

import jfx.core.state.{ListProperty, Property}
import reflect.{ClassDescriptor, ParameterizedTypeDescriptor}

import scala.scalajs.js

private[json] object JsonSerializer {

  def serialize(value: Any, context: JsonMappingContext): js.Any =
    if (value == null) null
    else {
      val expectedType = context.resolvedType

      expectedType match {
        case descriptor if JsonTypeModel.isRawJson(descriptor) =>
          value.asInstanceOf[js.Any]
        case descriptor if JsonTypeModel.isProperty(descriptor) =>
          serializeProperty(value, context)
        case descriptor if JsonTypeModel.isListProperty(descriptor) =>
          serializeListProperty(value, context)
        case descriptor if JsonTypeModel.isOption(descriptor) =>
          serializeOption(value, context)
        case descriptor if JsonTypeModel.isMap(descriptor) =>
          serializeMap(value, context)
        case descriptor if JsonTypeModel.isCollection(descriptor) =>
          serializeCollection(value, context)
        case descriptor if JsonTypeModel.isPrimitive(descriptor) =>
          JsonValueCodec.serializePrimitive(value, descriptor.typeName)
        case parameterized: ParameterizedTypeDescriptor =>
          serializeObject(value, parameterized.rawType, context)
        case descriptor: ClassDescriptor =>
          serializeObject(value, descriptor, context)
        case descriptor =>
          throw new IllegalArgumentException(
            s"Unsupported type descriptor for serialization: ${descriptor.typeName}"
          )
      }
    }

  private def serializeObject(
      model: Any,
      declaredDescriptor: ClassDescriptor,
      parentContext: JsonMappingContext
  ): js.Dynamic = {
    val runtimeDescriptor =
      JsonMetadata.descriptorForSerialization(model, declaredDescriptor)
    val context    = objectContext(parentContext)
    val properties = JsonMetadata.serializationProperties(runtimeDescriptor)
    val result     = js.Dictionary.empty[js.Any]

    JsonMetadata.typeName(runtimeDescriptor).foreach { typeName =>
      result(JsonMetadata.TypeField) = typeName
    }

    JsonMetadata.inlineMapProperty(properties) match {
      case Some(property) =>
        val propertyValue = JsonMetadata.accessor(runtimeDescriptor, property).get(model)
        val fieldContext  = context.child(property.propertyType)

        serializeMapEntries(propertyValue, fieldContext).foreach { case (key, value) =>
          result(key) = value
        }
      case None =>
        properties.foreach { property =>
          val propertyValue = JsonMetadata.accessor(runtimeDescriptor, property).get(model)
          val fieldContext  = context.child(property.propertyType)

          if (shouldSerialize(propertyValue, fieldContext, JsonMetadata.isId(property))) {
            result(JsonMetadata.fieldName(property)) = serialize(propertyValue, fieldContext)
          }
        }
    }

    result.asInstanceOf[js.Dynamic]
  }

  private def serializeProperty(value: Any, context: JsonMappingContext): js.Any = {
    val property  = value.asInstanceOf[Property[Any]]
    val innerType = JsonTypeModel.firstTypeArgument(context.resolvedType)
    serialize(property.get, context.child(innerType))
  }

  private def serializeListProperty(value: Any, context: JsonMappingContext): js.Any = {
    val property    = value.asInstanceOf[ListProperty[Any]]
    val elementType = JsonTypeModel.firstTypeArgument(context.resolvedType)

    js.Array(property.iterator.map(item => serialize(item, context.child(elementType))).toSeq*)
  }

  private def serializeOption(value: Any, context: JsonMappingContext): js.Any =
    value.asInstanceOf[Option[Any]] match {
      case Some(innerValue) =>
        serialize(innerValue, context.child(JsonTypeModel.firstTypeArgument(context.resolvedType)))
      case None =>
        null
    }

  private def serializeMap(value: Any, context: JsonMappingContext): js.Any =
    js.Dictionary(serializeMapEntries(value, context)*).asInstanceOf[js.Any]

  private def serializeMapEntries(
      value: Any,
      context: JsonMappingContext
  ): Seq[(String, js.Any)] = {
    val valueType = JsonTypeModel.secondTypeArgument(context.resolvedType)

    value match {
      case entries: scala.collection.Map[?, ?] =>
        entries.toSeq.map { case (key, entryValue) =>
          key.toString -> serialize(entryValue, context.child(valueType))
        }
      case other =>
        throw new IllegalArgumentException(
          s"Expected map for ${context.resolvedType.typeName}, got ${other.getClass.getName}"
        )
    }
  }

  private def serializeCollection(value: Any, context: JsonMappingContext): js.Any = {
    val elementType = JsonTypeModel.firstTypeArgument(context.resolvedType)
    val values      = value match {
      case array: js.Array[?]    => array.toSeq
      case array: Array[?]       => array.toSeq
      case iterable: Iterable[?] => iterable.toSeq
      case other                 =>
        throw new IllegalArgumentException(
          s"Expected collection for ${context.resolvedType.typeName}, got ${other.getClass.getName}"
        )
    }

    js.Array(values.map(item => serialize(item, context.child(elementType)))*).asInstanceOf[js.Any]
  }

  private def shouldSerialize(
      value: Any,
      context: JsonMappingContext,
      isId: Boolean
  ): Boolean =
    if (isId) {
      value match {
        case property: Property[?] => property.get != null
        case _                     => true
      }
    } else {
      value match {
        case property: Property[?] =>
          property.isDirty || hasDirtyPayload(
            property.get,
            context.child(propertyValueType(context))
          )
        case property: ListProperty[?] =>
          property.isDirty || property.iterator.exists { item =>
            hasDirtyPayload(item, context.child(listValueType(context)))
          }
        case _ =>
          true
      }
    }

  private def hasDirtyPayload(value: Any, context: JsonMappingContext): Boolean =
    if (value == null) false
    else {
      val expectedType = context.resolvedType

      if (JsonTypeModel.isProperty(expectedType)) {
        val property = value.asInstanceOf[Property[Any]]
        property.isDirty || hasDirtyPayload(
          property.get,
          context.child(JsonTypeModel.firstTypeArgument(expectedType))
        )
      } else if (JsonTypeModel.isListProperty(expectedType)) {
        val property    = value.asInstanceOf[ListProperty[Any]]
        val elementType = JsonTypeModel.firstTypeArgument(expectedType)
        property.isDirty || property.iterator.exists(item =>
          hasDirtyPayload(item, context.child(elementType))
        )
      } else if (JsonTypeModel.isOption(expectedType)) {
        value.asInstanceOf[Option[Any]].exists { item =>
          hasDirtyPayload(item, context.child(JsonTypeModel.firstTypeArgument(expectedType)))
        }
      } else if (JsonTypeModel.isMap(expectedType)) {
        val valueType = JsonTypeModel.secondTypeArgument(expectedType)
        value
          .asInstanceOf[scala.collection.Map[?, ?]]
          .valuesIterator
          .exists(item => hasDirtyPayload(item, context.child(valueType)))
      } else if (JsonTypeModel.isCollection(expectedType)) {
        val elementType = JsonTypeModel.firstTypeArgument(expectedType)
        collectionValues(value).exists(item => hasDirtyPayload(item, context.child(elementType)))
      } else {
        expectedType match {
          case parameterized: ParameterizedTypeDescriptor =>
            hasDirtyObject(value, parameterized.rawType, context)
          case descriptor: ClassDescriptor if !JsonTypeModel.isPrimitive(descriptor) =>
            hasDirtyObject(value, descriptor, context)
          case _ =>
            false
        }
      }
    }

  private def hasDirtyObject(
      model: Any,
      declaredDescriptor: ClassDescriptor,
      parentContext: JsonMappingContext
  ): Boolean = {
    val runtimeDescriptor =
      JsonMetadata.descriptorForSerialization(model, declaredDescriptor)
    val context = objectContext(parentContext)

    JsonMetadata
      .serializationProperties(runtimeDescriptor)
      .iterator
      .filterNot(JsonMetadata.isId)
      .exists { property =>
        val propertyValue = JsonMetadata.accessor(runtimeDescriptor, property).get(model)
        val fieldContext  = context.child(property.propertyType)
        shouldSerialize(propertyValue, fieldContext, isId = false)
      }
  }

  private def objectContext(parent: JsonMappingContext): JsonMappingContext =
    JsonMappingContext(
      parent.resolvedType,
      parent.bindings ++ JsonTypeModel.typeBindings(parent.resolvedType)
    )

  private def propertyValueType(context: JsonMappingContext) =
    JsonTypeModel.firstTypeArgument(context.resolvedType)

  private def listValueType(context: JsonMappingContext) =
    JsonTypeModel.firstTypeArgument(context.resolvedType)

  private def collectionValues(value: Any): Iterator[Any] =
    value match {
      case array: js.Array[?]    => array.iterator
      case array: Array[?]       => array.iterator
      case iterable: Iterable[?] => iterable.iterator
      case _                     => Iterator.empty
    }
}
