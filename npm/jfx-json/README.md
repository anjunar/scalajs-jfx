# @anjunar/jfx-json

Schema-driven JSON mapping for TypeScript models used with JFX3. It is the npm counterpart of `jfx-json` and keeps mapping explicit at the JavaScript boundary.

## Overview

TypeScript has no Scala reflection or annotations. `JsonSchema` therefore describes factories, fields, nested schemas, maps, arrays, and polymorphic subtypes explicitly. The mapper has no dependency on the Scala bridge; it only understands the `Property` and `ListProperty` shapes from `@anjunar/jfx-core`.

## Installation

```bash
npm install @anjunar/jfx-json @anjunar/jfx-core
```

## Quick start

```ts
import { property, type Property } from "@anjunar/jfx-core";
import { JsonMapper, jsonField, jsonSchema } from "@anjunar/jfx-json";

type Account = {
  id: Property<string>;
  name: Property<string>;
};

const schema = jsonSchema<Account>(
  () => ({ id: property(""), name: property("") }),
  { id: jsonField({ id: true }), name: jsonField({ name: "fullName" }) },
);

const account = schema.factory();
account.id.set("a-1");
account.name.set("Ada");
const json = JsonMapper.serialize(account, schema);
const restored = JsonMapper.deserialize(json, schema);
```

## Decorated classes

```ts
import { JsonId, JsonMapper, JsonProperty } from "@anjunar/jfx-json";

class Profile {
  @JsonId id = "";
  @JsonProperty("displayName") name = "";
}

const profile = JsonMapper.deserialize({ id: "p-1", displayName: "Ada" }, Profile);
const json = JsonMapper.serialize(profile);
```

Enable TypeScript `experimentalDecorators` for decorator syntax. `JsonSchema.abstractType` and `JsonType` provide `@type` discrimination for polymorphic models. `jsonProperty`, `jsonIgnore`, and `jsonId` are functional aliases for the decorators' field metadata.

## Core concepts

Unchanged `Property` values are omitted from dirty payloads; an ID field is retained. Plain fields are always mapped. `jsonField` can specify a nested `schema`, `itemSchema`, `valueSchema`, JSON name, direction flags, and `id`. Use `JsonMapper.deserializeArray` for JSON arrays.

## API overview

- `JsonMapper.serialize`, `deserialize`, `deserializeArray`
- `JsonSchema`, `jsonSchema`, `jsonField`
- `JsonProperty`, `JsonIgnore`, `JsonId`, `JsonType`
- `jsonProperty`, `jsonIgnore`, `jsonId`
- `JsonValue`, `JsonPrimitive`, `JsonField`

## Related modules

- [`@anjunar/jfx-core`](../jfx-core/README.md) supplies reactive property shapes.
- [`jfx-json`](../../jfx-json/README.md) provides the Scala implementation.
