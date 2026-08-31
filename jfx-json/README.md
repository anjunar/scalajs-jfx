# scalajs-jfx-json

`scalajs-jfx-json` maps reflected Scala models to native JavaScript JSON values
and back. It keeps the public JFX2 mapper API while using a new, stateless
implementation.

## Supported values

- plain reflected model properties
- `Property[T]` and `ListProperty[T]`
- `Option`, maps, Scala collections, Scala arrays, and `js.Array`
- strings, booleans, numeric values, chars, UUIDs, and raw `js.Any`
- parameterized models and polymorphic models marked with `JsonType`
- field names and mapping directions controlled through `JsonProperty` and
  `JsonIgnore`
- dirty payload serialization with stable identifiers marked by `JsonId`

## API

```scala
val mapper = JsonMapper()

val json = mapper.serialize(account)
val restored = mapper.deserialize[Account](json)
```

The companion object exposes the same inline operations:

```scala
val json = JsonMapper.serialize(account)
val restored = JsonMapper.deserialize[Account](json)
```

Models used through the inline API must be registered with the reflection
runtime so that property accessors and an instance factory are available.
Callers that already hold a `TypeDescriptor` can pass it explicitly:

```scala
val json = mapper.serialize(account, accountDescriptor)
val restored = mapper.deserialize[Account](json, accountDescriptor)
```

## Internal structure

- `JsonMapper` is the public facade.
- `JsonMappingContext` carries expected types and generic bindings.
- `JsonTypeModel` classifies and resolves reflected types.
- `JsonMetadata` handles annotations, fields, runtime descriptors, and
  polymorphism.
- `JsonValueCodec` validates JSON shapes and converts primitive values.
- `JsonSerializer` and `JsonDeserializer` implement the two mapping directions.
