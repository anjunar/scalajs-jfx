package jfx.json

import reflect.{Annotation, ClassDescriptor, PropertyAccessor, PropertyDescriptor}

import scala.scalajs.js

private[json] object JsonMetadata {

  val TypeField = "@type"

  private val IdField              = "id"
  private val JsonTypeAnnotation   = "jfx.json.JsonType"
  private val JsonNameAnnotation   = "jfx.json.JsonProperty"
  private val JsonIgnoreAnnotation = "jfx.json.JsonIgnore"
  private val JsonIdAnnotation     = "jfx.json.JsonId"

  def serializationProperties(descriptor: ClassDescriptor): Array[PropertyDescriptor] =
    descriptor.resolved.properties
      .filter(isPublicJsonProperty)
      .filter(isSerializable)

  def deserializationProperties(descriptor: ClassDescriptor): Array[PropertyDescriptor] =
    descriptor.resolved.properties
      .filter(isPublicJsonProperty)
      .filter(isDeserializable)

  def fieldName(property: PropertyDescriptor): String =
    annotationValue(property.annotations, JsonNameAnnotation).getOrElse(property.name)

  def typeName(descriptor: ClassDescriptor): Option[String] =
    annotationValue(descriptor.annotations, JsonTypeAnnotation)

  def isId(property: PropertyDescriptor): Boolean =
    property.hasAnnotation(JsonIdAnnotation)

  def accessor(
      owner: ClassDescriptor,
      property: PropertyDescriptor
  ): PropertyAccessor[Any, Any] =
    owner.requirePropertyAccessor(property.name).asInstanceOf[PropertyAccessor[Any, Any]]

  def inlineMapProperty(
      properties: Array[PropertyDescriptor]
  ): Option[PropertyDescriptor] = {
    val assignableProperties = properties.filter { property =>
      property.isWriteable || property.accessor.exists(_.hasSetter)
    }

    assignableProperties match {
      case Array(property) if JsonTypeModel.isMap(property.propertyType) => Some(property)
      case _                                                             => None
    }
  }

  def descriptorForSerialization(
      value: Any,
      declaredDescriptor: ClassDescriptor
  ): ClassDescriptor = {
    val declared = declaredDescriptor.resolved
    if (!declared.isAbstract) declared
    else runtimeDescriptor(value, declared)
  }

  def descriptorForDeserialization(
      declaredDescriptor: ClassDescriptor,
      jsonObject: js.Dictionary[js.Any]
  ): ClassDescriptor = {
    val declared   = declaredDescriptor.resolved
    val candidates = subtypeCandidates(declared)
    val jsonType   = jsonObject.get(TypeField).map(_.toString)
    val jsonId     = jsonObject.get(IdField).map(_.toString)

    val byId   = jsonId.flatMap(id => candidates.find(matchesTypeName(_, id)))
    val byType = jsonType.flatMap { typeName =>
      candidates
        .find(matchesTypeName(_, typeName))
        .orElse {
          ClassDescriptor
            .maybeForName(typeName)
            .filter(candidate => isAssignableTo(candidate, declared.typeName))
        }
    }

    if (declared.isAbstract) {
      byId
        .orElse(byType)
        .getOrElse {
          jsonType match {
            case Some(typeName) =>
              throw new IllegalArgumentException(
                s"Unknown @type '$typeName' for ${declared.typeName}"
              )
            case None =>
              throw new IllegalArgumentException(
                s"Missing @type for abstract type ${declared.typeName}"
              )
          }
        }
    } else {
      jsonType match {
        case Some(typeName) =>
          byType.getOrElse {
            throw new IllegalArgumentException(
              s"Unknown @type '$typeName' for ${declared.typeName}"
            )
          }
        case None =>
          declared
      }
    }
  }

  private def runtimeDescriptor(value: Any, declared: ClassDescriptor): ClassDescriptor = {
    val runtimeNames = candidateRuntimeNames(value)
    val registered   = runtimeNames.iterator
      .flatMap(ClassDescriptor.maybeForName)
      .toSeq
      .headOption

    registered
      .orElse {
        subtypeCandidates(declared).find { candidate =>
          runtimeNames.contains(candidate.typeName) || runtimeNames.contains(candidate.simpleName)
        }
      }
      .getOrElse {
        throw new IllegalArgumentException(
          s"Cannot resolve runtime descriptor for value of ${runtimeNames.headOption.getOrElse(value.getClass.getName)} as ${declared.typeName}"
        )
      }
  }

  private def subtypeCandidates(descriptor: ClassDescriptor): List[ClassDescriptor] =
    (ClassDescriptor.maybeResolve(descriptor).toList ++
      ClassDescriptor.all
        .filter(candidate => isAssignableTo(candidate, descriptor.typeName))
        .toList)
      .distinctBy(_.typeName)

  private def isAssignableTo(descriptor: ClassDescriptor, superTypeName: String): Boolean =
    descriptor.typeName == superTypeName || descriptor.isAssignableTo(superTypeName)

  private def matchesTypeName(descriptor: ClassDescriptor, jsonType: String): Boolean = {
    val descriptorNames =
      Set(descriptor.typeName, descriptor.simpleName) ++ typeName(descriptor).toSet
    val jsonNames = typeNameTokens(jsonType)

    descriptorNames.exists(name => jsonNames.contains(name) || jsonNames.contains(name.trim))
  }

  private def typeNameTokens(value: String): Set[String] =
    if (value == null || value.isBlank) Set.empty
    else {
      val normalized = value.trim
      val localName  = normalized
        .split("[/#:]")
        .iterator
        .filter(_.nonEmpty)
        .toSeq
        .lastOption
        .getOrElse(normalized)

      Set(normalized, localName)
    }

  private def candidateRuntimeNames(value: Any): Vector[String] = {
    val javaClass       = value.getClass
    val dynamic         = value.asInstanceOf[js.Dynamic]
    val constructorName =
      if (
        js.isUndefined(dynamic.selectDynamic("constructor")) ||
        js.isUndefined(dynamic.selectDynamic("constructor").selectDynamic("name"))
      ) None
      else Option(dynamic.selectDynamic("constructor").selectDynamic("name").toString)

    Vector(
      Option(javaClass.getName),
      Option(javaClass.getSimpleName),
      constructorName
    ).flatten.distinct
  }

  private def isPublicJsonProperty(property: PropertyDescriptor): Boolean =
    property.isPublic &&
      property.name.nonEmpty &&
      !property.name.contains("$") &&
      property.name.forall(character => character.isLetterOrDigit || character == '_')

  private def isSerializable(property: PropertyDescriptor): Boolean =
    ignoreFlag(property, "serializable").getOrElse(!property.hasAnnotation(JsonIgnoreAnnotation))

  private def isDeserializable(property: PropertyDescriptor): Boolean =
    ignoreFlag(property, "deserializable").getOrElse(!property.hasAnnotation(JsonIgnoreAnnotation))

  private def ignoreFlag(
      property: PropertyDescriptor,
      parameterName: String
  ): Option[Boolean] =
    property.annotations
      .find(_.annotationClassName == JsonIgnoreAnnotation)
      .flatMap(_.parameters.get(parameterName))
      .map {
        case value: Boolean => value
        case value          => value.toString.toBoolean
      }

  private def annotationValue(
      annotations: Array[Annotation],
      annotationClassName: String
  ): Option[String] =
    annotations
      .find(_.annotationClassName == annotationClassName)
      .flatMap(_.parameters.get("value"))
      .map(_.toString)
}
