package jfx.json

import jfx.core.state.{ListProperty, Property, PropertyAccess, ReadOnlyProperty}
import jfx.form.Model

import scala.scalajs.js
import scala.scalajs.js.{Dynamic, undefined}


class JsonMapper(val registry : JsonRegistry) {

  def deserialize[M <: Model[M]](dynamic : Dynamic): M = {
    val entityType = dynamic.selectDynamic("@type").asInstanceOf[String]
    val entity = registry.classes.get(entityType).get.apply().asInstanceOf[M]

    js.Object.keys(dynamic.asInstanceOf[js.Object]).filter(key => ! key.startsWith("@")).foreach(key => {
      val value = dynamic.selectDynamic(key)

      if (value.selectDynamic("@type") == undefined) {
        if (value.isInstanceOf[js.Array[?]]) {
          val property = entity.findProperty[ListProperty[Any]](key)
          value.asInstanceOf[js.Array[Dynamic]].foreach(elem => {
            val deserializedObject : M = deserialize(elem)
            property.addOne(deserializedObject)
          })
        } else {
          val property = entity.findProperty[Property[Any]](key)
          property.set(value)
        }
      } else {
        val deserializedValue : M = deserialize(value)
        val property = entity.findProperty[Property[Any]](key)
        property.set(deserializedValue)
      }
    })


    entity
  }

  def serialize(model : Model[?]): Dynamic = {
    val out = js.Dictionary[js.Any]()

    out.update("@type", model.getClass.getSimpleName)

    val props = model.properties.asInstanceOf[js.Array[PropertyAccess[Any, Any]]]
    props.foreach { access =>
      access.get(model) match {
        case Some(propertyOrValue) =>
          val serialized = serializeValue(propertyOrValue)
          if (serialized != null && !js.isUndefined(serialized)) {
            out.update(access.name, serialized)
          }
        case None => ()
      }
    }

    out.asInstanceOf[js.Dynamic]
  }

  private def serializeValue(value: Any): js.Any =
    value match {
      case null =>
        null

      case p: ReadOnlyProperty[?] =>
        serializeValue(p.get)

      case m: Model[?] =>
        serialize(m)

      case arr: js.Array[?] =>
        arr.map(elem => serializeValue(elem))

      case v =>
        v.asInstanceOf[js.Any]
    }

}
