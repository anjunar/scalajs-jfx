# scalajs-jfx-json

`scalajs-jfx-json` maps reflected Scala models to native JavaScript JSON values
and back. The mapper is stateless and does not use `ReflectClassLoader` or the
global `ClassDescriptor` registry.

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
given JsonSchema[Account] = JsonSchema(() => Account())

val mapper = JsonMapper()

val json = mapper.serialize(account)
val restored = mapper.deserialize[Account](json)
```

The companion object exposes the same inline operations:

```scala
val json = JsonMapper.serialize(account)
val restored = JsonMapper.deserialize[Account](json)
```

`JsonSchema` contains the reflected accessors, runtime class, and instance
factory. Nested schemas are attached explicitly:

```scala
val addressSchema = JsonSchema(() => Address())

given JsonSchema[Account] =
  JsonSchema(() => Account()).withDependencies(addressSchema)
```

Polymorphic subtype sets are local as well:

```scala
val circleSchema = JsonSchema(() => Circle())

given JsonSchema[Shape] =
  JsonSchema.abstractType[Shape](circleSchema)
```

No global registration order is involved. Callers that already hold a fully
bound `TypeDescriptor` can still pass it explicitly:

```scala
val json = mapper.serialize(account, accountDescriptor)
val restored = mapper.deserialize[Account](json, accountDescriptor)
```

## Internal structure

- `JsonMapper` is the public facade.
- `JsonSchema` owns factories, accessors, dependencies, and explicit subtypes.
- `JsonSchemaCatalog` provides mapper-local schema resolution.
- `JsonMappingContext` carries expected types and generic bindings.
- `JsonTypeModel` classifies and resolves reflected types.
- `JsonMetadata` handles annotations, fields, runtime descriptors, and
  polymorphism.
- `JsonValueCodec` validates JSON shapes and converts primitive values.
- `JsonSerializer` and `JsonDeserializer` implement the two mapping directions.
