package jfx.json

import reflect.TypeDescriptor
import reflect.macros.ReflectMacros.reflectType

import scala.scalajs.js
import scala.scalajs.js.Dynamic

class JsonMapper {

  inline def serialize[M](model: M): Dynamic =
    JsonMapper.serialize(model)

  def serialize[M](model: M, meta: TypeDescriptor): Dynamic =
    JsonMapper.serialize(model, meta)

  inline def deserialize[M](json: Dynamic): M =
    JsonMapper.deserialize[M](json)

  def deserialize[M](json: Dynamic, meta: TypeDescriptor): M =
    JsonMapper.deserialize(json, meta)

  def deserializeArray[M](json: js.Array[js.Dynamic], meta: TypeDescriptor): Seq[M] =
    JsonMapper.deserializeArray(json, meta)
}

object JsonMapper {

  inline def serialize[M](model: M): Dynamic =
    serialize(model, reflectType[M])

  def serialize[M](model: M, meta: TypeDescriptor): Dynamic =
    JsonSerializer.serialize(model, JsonMappingContext.root(meta)).asInstanceOf[Dynamic]

  inline def deserialize[M](json: Dynamic): M =
    deserialize(json, reflectType[M])

  def deserialize[M](json: Dynamic, meta: TypeDescriptor): M =
    JsonDeserializer.deserialize(json, JsonMappingContext.root(meta)).asInstanceOf[M]

  def deserializeArray[M](json: js.Array[js.Dynamic], meta: TypeDescriptor): Seq[M] =
    if (json == null || js.isUndefined(json)) Seq.empty
    else
      json.toSeq.map { value =>
        JsonDeserializer.deserialize(value, JsonMappingContext.root(meta)).asInstanceOf[M]
      }
}
