# scalajs-jfx-json

Schema-driven JSON mapping for reflected Scala models and JFX properties. The mapper converts models to native JavaScript JSON values and back without a global descriptor registry.

## Overview

`jfx-json` uses an explicit `JsonSchema` for factories, reflected accessors, dependencies, and polymorphic subtypes. `JsonMapper` is stateless; schema resolution is local to the mapper and the schema graph. It supports plain fields, `Property`, `ListProperty`, options, maps, Scala collections, arrays, `js.Array`, primitives, parameterized models, and polymorphic models.

## Installation

```scala
libraryDependencies += "com.anjunar" %% "scalajs-jfx-json" % "3.0.0-SNAPSHOT"
```

## Quick start

```scala
import jfx.core.state.Property
import jfx.json.{JsonMapper, JsonSchema}

final case class Account(
    id: Property[String] = Property(""),
    name: Property[String] = Property("")
)

given JsonSchema[Account] = JsonSchema(() => Account())

val account = Account()
account.id.set("a-1")
account.name.set("Ada")

val json = JsonMapper.serialize(account)
val restored = JsonMapper.deserialize[Account](json)
```

## Usage

Attach nested schemas explicitly and declare polymorphic subtype sets locally:

```scala
val addressSchema = JsonSchema(() => Address())
given JsonSchema[Account] =
  JsonSchema(() => Account()).withDependencies(addressSchema)

val circleSchema = JsonSchema(() => Circle())
given JsonSchema[Shape] = JsonSchema.abstractType[Shape](circleSchema)
```

`JsonProperty` changes the JSON field name. `JsonIgnore` controls serialization and deserialization independently. `JsonId` retains a stable identifier when dirty-payload serialization omits unchanged properties. `JsonType` supplies the `@type` discriminator for polymorphic models.

## Core concepts

The mapper treats `Property` and `ListProperty` values as state-bearing fields. Unchanged properties are omitted from a dirty payload, while identifiers remain available. Plain fields are mapped normally. A fully bound `TypeDescriptor` can be supplied explicitly when the caller already has one.

## API overview

- `JsonMapper` — serialize and deserialize models and arrays.
- `JsonSchema` — factories, field metadata, dependencies, and subtypes.
- `JsonProperty`, `JsonIgnore`, `JsonId`, `JsonType` — mapping annotations.
- `JsonSerializer`, `JsonDeserializer`, `JsonValueCodec` — mapping internals used by the facade.

## Related modules

- [`jfx-core`](../jfx-core/README.md) provides `Property` and `ListProperty`.
- [`@anjunar/jfx-json`](../npm/jfx-json/README.md) exposes the same mapping concepts to TypeScript.
