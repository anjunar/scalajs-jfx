package jfx.json

import reflect.TypeDescriptor

import scala.scalajs.js
import scala.scalajs.js.Dynamic

class JsonMapper private (private val schemas: JsonSchemaCatalog) {

  def this() =
    this(JsonSchemaCatalog.empty)

  inline def serialize[M](model: M)(using schema: JsonSchema[M]): Dynamic =
    JsonSerializer
      .serialize(model, JsonMappingContext.root(schema.descriptor, schemas.add(schema)))
      .asInstanceOf[Dynamic]

  def serialize[M](model: M, meta: TypeDescriptor): Dynamic =
    JsonSerializer.serialize(model, JsonMappingContext.root(meta, schemas)).asInstanceOf[Dynamic]

  inline def deserialize[M](json: Dynamic)(using schema: JsonSchema[M]): M =
    JsonDeserializer
      .deserialize(json, JsonMappingContext.root(schema.descriptor, schemas.add(schema)))
      .asInstanceOf[M]

  def deserialize[M](json: Dynamic, meta: TypeDescriptor): M =
    JsonDeserializer.deserialize(json, JsonMappingContext.root(meta, schemas)).asInstanceOf[M]

  def deserializeArray[M](json: js.Array[js.Dynamic], schema: JsonSchema[M]): Seq[M] =
    deserializeArrayWithContext(
      json,
      JsonMappingContext.root(schema.descriptor, schemas.add(schema))
    )

  def deserializeArray[M](json: js.Array[js.Dynamic], meta: TypeDescriptor): Seq[M] =
    deserializeArrayWithContext(json, JsonMappingContext.root(meta, schemas))

  private def deserializeArrayWithContext[M](
      json: js.Array[js.Dynamic],
      context: JsonMappingContext
  ): Seq[M] =
    if (json == null || js.isUndefined(json)) Seq.empty
    else json.toSeq.map(value => JsonDeserializer.deserialize(value, context).asInstanceOf[M])
}

object JsonMapper {

  def apply(schemas: JsonSchema[?]*): JsonMapper =
    new JsonMapper(JsonSchemaCatalog.from(schemas))

  inline def serialize[M](model: M)(using schema: JsonSchema[M]): Dynamic =
    JsonMapper(schema).serialize(model)(using schema)

  def serialize[M](model: M, meta: TypeDescriptor): Dynamic =
    JsonMapper().serialize(model, meta)

  inline def deserialize[M](json: Dynamic)(using schema: JsonSchema[M]): M =
    JsonMapper(schema).deserialize(json)(using schema)

  def deserialize[M](json: Dynamic, meta: TypeDescriptor): M =
    JsonMapper().deserialize(json, meta)

  def deserializeArray[M](json: js.Array[js.Dynamic], meta: TypeDescriptor): Seq[M] =
    JsonMapper().deserializeArray(json, meta)
}
