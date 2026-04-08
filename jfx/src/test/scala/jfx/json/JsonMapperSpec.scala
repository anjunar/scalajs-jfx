package jfx.json

import jfx.test.{SimpleModel, NestedModel, ParentModel, ListModel, BooleanModel, DoubleModel, OptionModel, MapModel, Item, GenericContainer, TestModelRegistry, TestPost}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import reflect.macros.ReflectMacros
import reflect.ReflectRegistry
import scala.scalajs.js
import scala.scalajs.js.Dynamic

class JsonMapperSpec extends AnyFlatSpec with Matchers {

  // Ensure test models are registered
  TestModelRegistry

  "JsonMapper" should "ein einfaches Modell serialisieren" in {
    reflect.ReflectRegistry.register(() => new SimpleModel())
    
    val model = new SimpleModel
    model.name.set("Test")
    model.value.set(42)

    val mapper = new JsonMapper
    val json = mapper.serialize(model)

    json.name.toString shouldBe "Test"
    json.value.toString shouldBe "42"
  }

  it should "ein einfaches Modell deserialisieren" in {
    reflect.ReflectRegistry.register(() => new SimpleModel())
    
    val mapper = new JsonMapper
    val json = Dynamic.literal(
      name = "Deserialized",
      value = 100
    )

    val model = mapper.deserialize[SimpleModel](json, ReflectMacros.reflectType[SimpleModel])

    model.name.get shouldBe "Deserialized"
    model.value.get shouldBe 100
  }

  it should "ein Modell mit verschachteltem Modell serialisieren" in {
    reflect.ReflectRegistry.register(() => new NestedModel())
    reflect.ReflectRegistry.register(() => new ParentModel())
    
    val nested = new NestedModel
    nested.title.set("Nested")
    nested.count.set(5)

    val model = new ParentModel
    model.name.set("Parent")
    model.nested.set(nested)

    val mapper = new JsonMapper
    val json = mapper.serialize(model)

    json.name.toString shouldBe "Parent"
    json.nested.title.toString shouldBe "Nested"
    json.nested.count.toString shouldBe "5"
  }

  it should "ein Modell mit verschachteltem Modell deserialisieren" in {
    reflect.ReflectRegistry.register(() => new NestedModel())
    reflect.ReflectRegistry.register(() => new ParentModel())
    
    val mapper = new JsonMapper
    val nestedJson = Dynamic.literal(
      title = "Nested Deserialized",
      count = 10
    )
    val json = Dynamic.literal(
      name = "Parent Deserialized",
      nested = nestedJson
    )

    val model = mapper.deserialize[ParentModel](json, ReflectMacros.reflectType[ParentModel])

    model.name.get shouldBe "Parent Deserialized"
    model.nested.get.title.get shouldBe "Nested Deserialized"
    model.nested.get.count.get shouldBe 10
  }

  it should "ein Modell mit ListProperty serialisieren" in {
    reflect.ReflectRegistry.register(() => new ListModel())
    
    val model = new ListModel
    model.name.set("List Test")
    model.items.addOne("Item 1")
    model.items.addOne("Item 2")
    model.items.addOne("Item 3")

    val mapper = new JsonMapper
    val json = mapper.serialize(model)

    json.name.toString shouldBe "List Test"
    val items = json.items.asInstanceOf[js.Array[String]]
    items.length shouldBe 3
    items(0) shouldBe "Item 1"
    items(1) shouldBe "Item 2"
    items(2) shouldBe "Item 3"
  }

  it should "ein Modell mit ListProperty deserialisieren" in {
    reflect.ReflectRegistry.register(() => new ListModel())
    
    val mapper = new JsonMapper
    val json = Dynamic.literal(
      name = "List Deserialized",
      items = js.Array("A", "B", "C", "D")
    )

    val model = mapper.deserialize[ListModel](json, ReflectMacros.reflectType[ListModel])

    model.name.get shouldBe "List Deserialized"
    model.items.size shouldBe 4
    model.items.get(0) shouldBe "A"
    model.items.get(1) shouldBe "B"
    model.items.get(2) shouldBe "C"
    model.items.get(3) shouldBe "D"
  }

  it should "ein leeres Array deserialisieren" in {
    reflect.ReflectRegistry.register(() => new ListModel())
    
    val mapper = new JsonMapper
    val json = Dynamic.literal(
      name = "Empty List",
      items = js.Array()
    )

    val model = mapper.deserialize[ListModel](json, ReflectMacros.reflectType[ListModel])

    model.name.get shouldBe "Empty List"
    model.items.size shouldBe 0
  }

  it should "null Array als leere Sequenz deserialisieren" in {
    val result = JsonMapper.deserializeArray[SimpleModel](null, ReflectMacros.reflectType[SimpleModel])
    result shouldBe empty
  }

  it should "undefined Array als leere Sequenz deserialisieren" in {
    val result = JsonMapper.deserializeArray[SimpleModel](js.undefined.asInstanceOf[js.Array[js.Dynamic]], ReflectMacros.reflectType[SimpleModel])
    result shouldBe empty
  }

  it should "ein Array von Modellen deserialisieren" in {
    reflect.ReflectRegistry.register(() => new SimpleModel())
    
    val json1 = Dynamic.literal(name = "First", value = 1)
    val json2 = Dynamic.literal(name = "Second", value = 2)
    val json3 = Dynamic.literal(name = "Third", value = 3)

    val jsonArray = js.Array(json1, json2, json3).asInstanceOf[js.Array[js.Dynamic]]

    val models = JsonMapper.deserializeArray[SimpleModel](jsonArray, ReflectMacros.reflectType[SimpleModel])

    models.length shouldBe 3
    models(0).name.get shouldBe "First"
    models(0).value.get shouldBe 1
    models(1).name.get shouldBe "Second"
    models(1).value.get shouldBe 2
    models(2).name.get shouldBe "Third"
    models(2).value.get shouldBe 3
  }

  it should "Boolean Property serialisieren und deserialisieren" in {
    reflect.ReflectRegistry.register(() => new BooleanModel())
    
    val model = new BooleanModel
    model.active.set(true)
    model.name.set("Boolean Test")

    val mapper = new JsonMapper
    val json = mapper.serialize(model)
    json.name.toString shouldBe "Boolean Test"
    json.active.toString shouldBe "true"

    val deserializedJson = Dynamic.literal(
      name = "Boolean Deserialized",
      active = false
    )
    val deserialized = mapper.deserialize[BooleanModel](deserializedJson, ReflectMacros.reflectType[BooleanModel])
    deserialized.name.get shouldBe "Boolean Deserialized"
    deserialized.active.get shouldBe false
  }

  it should "Double Property serialisieren und deserialisieren" in {
    reflect.ReflectRegistry.register(() => new DoubleModel())
    
    val model = new DoubleModel
    model.name.set("Double Test")
    model.amount.set(3.14159)

    val mapper = new JsonMapper
    val json = mapper.serialize(model)
    json.name.toString shouldBe "Double Test"
    json.amount.toString shouldBe "3.14159"

    val deserializedJson = Dynamic.literal(
      name = "Double Deserialized",
      amount = 2.71828
    )
    val deserialized = mapper.deserialize[DoubleModel](deserializedJson, ReflectMacros.reflectType[DoubleModel])
    deserialized.name.get shouldBe "Double Deserialized"
    deserialized.amount.get shouldBe 2.71828
  }

  it should "Option Property serialisieren und deserialisieren" in {
    reflect.ReflectRegistry.register(() => new OptionModel())

    val model = new OptionModel
    model.name.set("Option Test")
    model.description.set(Some("Has Value"))

    val mapper = new JsonMapper
    val json = mapper.serialize(model)
    json.name.toString shouldBe "Option Test"
    json.description.toString shouldBe "Has Value"

    val deserializedJsonWithSome = Dynamic.literal(
      name = "Option Some",
      description = "Deserialized Some"
    )
    val deserializedSome = mapper.deserialize[OptionModel](deserializedJsonWithSome, ReflectMacros.reflectType[OptionModel])
    deserializedSome.name.get shouldBe "Option Some"
    deserializedSome.description.get shouldBe Some("Deserialized Some")

    val deserializedJsonWithNone = Dynamic.literal(
      name = "Option None",
      description = null
    )
    val deserializedNone = mapper.deserialize[OptionModel](deserializedJsonWithNone, ReflectMacros.reflectType[OptionModel])
    deserializedNone.name.get shouldBe "Option None"
    deserializedNone.description.get shouldBe None
  }

  it should "Map Property serialisieren und deserialisieren" in {
    reflect.ReflectRegistry.register(() => new MapModel())
    
    val model = new MapModel
    model.name.set("Map Test")
    model.metadata.set(Map("key1" -> "value1", "key2" -> "value2"))

    val mapper = new JsonMapper
    val json = mapper.serialize(model)
    json.name.toString shouldBe "Map Test"
    val metadata = json.metadata.asInstanceOf[js.Dynamic]
    metadata.key1.toString shouldBe "value1"
    metadata.key2.toString shouldBe "value2"

    val deserializedJson = Dynamic.literal(
      name = "Map Deserialized",
      metadata = Dynamic.literal(
        keyA = "valueA",
        keyB = "valueB",
        keyC = "valueC"
      )
    )
    val deserialized = mapper.deserialize[MapModel](deserializedJson, ReflectMacros.reflectType[MapModel])
    deserialized.name.get shouldBe "Map Deserialized"
    deserialized.metadata.get.size shouldBe 3
    deserialized.metadata.get.getOrElse("keyA", "") shouldBe "valueA"
    deserialized.metadata.get.getOrElse("keyB", "") shouldBe "valueB"
    deserialized.metadata.get.getOrElse("keyC", "") shouldBe "valueC"
  }

  it should "die statischen Methoden verwenden" in {
    reflect.ReflectRegistry.register(() => new SimpleModel())
    
    val model = new SimpleModel
    model.name.set("Static Test")
    model.value.set(999)

    val json = JsonMapper.serialize(model)
    json.name.toString shouldBe "Static Test"
    json.value.toString shouldBe "999"

    val deserializedJson = Dynamic.literal(name = "Static Deserialized", value = 777)
    val deserialized = JsonMapper.deserialize[SimpleModel](deserializedJson, ReflectMacros.reflectType[SimpleModel])
    deserialized.name.get shouldBe "Static Deserialized"
    deserialized.value.get shouldBe 777

    val jsonArray = js.Array(
      Dynamic.literal(name = "Static 1", value = 11),
      Dynamic.literal(name = "Static 2", value = 22)
    ).asInstanceOf[js.Array[js.Dynamic]]
    val models = JsonMapper.deserializeArray[SimpleModel](jsonArray, ReflectMacros.reflectType[SimpleModel])
    models.length shouldBe 2
    models(0).name.get shouldBe "Static 1"
    models(0).value.get shouldBe 11
    models(1).name.get shouldBe "Static 2"
    models(1).value.get shouldBe 22
  }

  it should "generische Typ-Informationen bei der Serialisierung erhalten" in {
    val item = new Item
    item.itemName.set("Test Item")
    item.price.set(19.99)

    val container = new GenericContainer[Item]
    container.containerName.set("Item Container")
    container.data.set(item)
    container.score.set(0.95)

    val mapper = new JsonMapper
    val meta = ReflectMacros.reflectType[GenericContainer[Item]]
    val json = mapper.serialize(container, meta)

    json.containerName.toString shouldBe "Item Container"
    json.score.toString shouldBe "0.95"
    json.data.itemName.toString shouldBe "Test Item"
    json.data.price.toString shouldBe "19.99"
  }

  it should "generische Typ-Informationen bei der Deserialisierung erhalten" in {
    val itemJson = Dynamic.literal(
      itemName = "Deserialized Item",
      price = 29.99
    )
    val containerJson = Dynamic.literal(
      containerName = "Deserialized Container",
      data = itemJson,
      score = 0.87
    )

    val mapper = new JsonMapper
    // Use the parameterized type descriptor to preserve generic type information
    val meta = ReflectMacros.reflectType[GenericContainer[Item]]
    val container = mapper.deserialize[GenericContainer[Item]](containerJson, meta)

    container.containerName.get shouldBe "Deserialized Container"
    container.score.get shouldBe 0.87
    container.data.get.itemName.get shouldBe "Deserialized Item"
    container.data.get.price.get shouldBe 29.99
  }

  it should "eine geerbte Property mit JsonName deserialisieren" in {
    val mapper = new JsonMapper
    val json = Dynamic.literal(title = "Timeline")
    json.updateDynamic("$links")(
      js.Array(
        Dynamic.literal(rel = "like"),
        Dynamic.literal(rel = "send-to-curation")
      )
    )

    val post = mapper.deserialize[TestPost](json, ReflectMacros.reflectType[TestPost])

    post.title.get shouldBe "Timeline"
    post.links.size shouldBe 2
    post.links.get(0).rel.get shouldBe "like"
    post.links.get(1).rel.get shouldBe "send-to-curation"
  }
}
