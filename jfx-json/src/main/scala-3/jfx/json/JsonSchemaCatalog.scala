package jfx.json

import reflect.ClassDescriptor

private[json] final class JsonSchemaCatalog private (
    private val schemasByTypeName: Map[String, JsonSchema[?]],
    private val subtypesByTypeName: Map[String, Vector[ClassDescriptor]]
) {

  def add(schema: JsonSchema[?]): JsonSchemaCatalog =
    JsonSchemaCatalog.from(
      schemasByTypeName.valuesIterator
        .filterNot(_.descriptor.typeName == schema.descriptor.typeName)
        .toSeq :+ schema
    )

  def resolve(descriptor: ClassDescriptor): ClassDescriptor =
    schemasByTypeName.get(descriptor.typeName).map(_.descriptor).getOrElse(descriptor)

  def find(typeName: String): Option[ClassDescriptor] =
    schemasByTypeName.get(typeName).map(_.descriptor)

  def candidatesFor(superTypeName: String): Vector[ClassDescriptor] =
    subtypesByTypeName.getOrElse(superTypeName, Vector.empty)

  def isAllowedSubtype(candidate: ClassDescriptor, declared: ClassDescriptor): Boolean =
    candidate.typeName == declared.typeName ||
      candidatesFor(declared.typeName).exists(_.typeName == candidate.typeName)
}

private[json] object JsonSchemaCatalog {

  val empty: JsonSchemaCatalog =
    new JsonSchemaCatalog(Map.empty, Map.empty)

  def from(schemas: Seq[JsonSchema[?]]): JsonSchemaCatalog = {
    def collect(
        remaining: List[JsonSchema[?]],
        collected: Map[String, JsonSchema[?]]
    ): Map[String, JsonSchema[?]] =
      remaining match {
        case Nil =>
          collected
        case schema :: tail if collected.contains(schema.descriptor.typeName) =>
          collect(tail, collected)
        case schema :: tail =>
          collect(
            schema.dependencies.toList ++ tail,
            collected.updated(schema.descriptor.typeName, schema)
          )
      }

    val collected = collect(schemas.toList, Map.empty)
    val subtypes  = collected.valuesIterator
      .filter(_.subtypes.nonEmpty)
      .map { schema =>
        schema.descriptor.typeName -> schema.subtypes.map(_.descriptor)
      }
      .toMap

    new JsonSchemaCatalog(collected, subtypes)
  }
}
