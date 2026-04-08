package jfx.test

import jfx.core.state.{ListProperty, Property}
import jfx.json.JsonName
import reflect.ReflectRegistry

class SimpleModel {
  val name: Property[String] = Property("")
  val value: Property[Int] = Property(0)
}

class NestedModel {
  val title: Property[String] = Property("")
  val count: Property[Int] = Property(0)
}

class ParentModel {
  val name: Property[String] = Property("")
  val nested: Property[NestedModel] = Property(new NestedModel)
}

class ListModel {
  val name: Property[String] = Property("")
  val items: ListProperty[String] = new ListProperty[String]()
}

class BooleanModel {
  val name: Property[String] = Property("")
  val active: Property[Boolean] = Property(false)
}

class DoubleModel {
  val name: Property[String] = Property("")
  val amount: Property[Double] = Property(0.0)
}

class OptionModel {
  val name: Property[String] = Property("")
  val description: Property[Option[String]] = Property(None)
}

class MapModel {
  val name: Property[String] = Property("")
  val metadata: Property[Map[String, String]] = Property(Map.empty[String, String])
}

class Item {
  val itemName: Property[String] = Property("")
  val price: Property[Double] = Property(0.0)
}

class GenericContainer[E] {
  val containerName: Property[String] = Property("")
  val data: Property[E] = Property(null.asInstanceOf[E])
  val score: Property[Double] = Property(1.0)
}

class User {
  val nickName: Property[String] = Property("")
}

class Data[E] {
  val data: Property[E] = Property(null.asInstanceOf[E])
  val score: Property[Double] = Property(1.0)
}

class Table[E] {
  val rows: ListProperty[E] = new ListProperty[E]()
  val size: Property[Int] = Property(0)
}

class TestJsonLink {
  val rel: Property[String] = Property("")
}

abstract class TestJsonEntity {
  @JsonName("$links")
  val links: ListProperty[TestJsonLink] = ListProperty()
}

class TestPost extends TestJsonEntity {
  val title: Property[String] = Property("")
}

object TestModelRegistry {
  println("=== Registering Test Models ===")
  val simpleDesc = ReflectRegistry.register(() => new SimpleModel())
  println(s"Registered SimpleModel with typeName: ${simpleDesc.typeName}")

  ReflectRegistry.register(() => new NestedModel())
  ReflectRegistry.register(() => new ParentModel())
  ReflectRegistry.register(() => new ListModel())
  ReflectRegistry.register(() => new BooleanModel())
  ReflectRegistry.register(() => new DoubleModel())
  ReflectRegistry.register(() => new OptionModel())
  ReflectRegistry.register(() => new MapModel())
  ReflectRegistry.register(() => new Item())
  // Register GenericContainer - the macro will capture type parameters
  val genericContainerDesc = ReflectRegistry.register(() => new GenericContainer[Any]())
  println(s"Registered GenericContainer with typeName: ${genericContainerDesc.typeName}")
  println(s"GenericContainer typeParameters: ${genericContainerDesc.typeParameters.mkString(", ")}")

  ReflectRegistry.register(() => new User())
  ReflectRegistry.register(() => new Data[User]())
  ReflectRegistry.register(() => new Table[Data[User]]())
  ReflectRegistry.register(() => new TestJsonLink())
  ReflectRegistry.register(() => new TestPost())

  println("=== Done Registering ===")
}
