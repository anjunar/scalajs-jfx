# @anjunar/jfx-json

Schema driven JSON mapping for TypeScript models used with JFX3. The package
is the npm counterpart of `jfx-json`: it keeps the mapper explicit at the
JavaScript boundary, where TypeScript has no Scala reflection or annotations.

```ts
import { JsonMapper, jsonField, jsonSchema } from "@anjunar/jfx-json";
import { property } from "@anjunar/jfx-core";

type Account = { id: ReturnType<typeof property<string>>; name: ReturnType<typeof property<string>> };
const accountSchema = jsonSchema<Account>(
  () => ({ id: property(""), name: property("") }),
  { id: jsonField({ id: true }), name: jsonField({ name: "fullName" }) },
);

const account = accountSchema.factory();
account.id.set("a-1");
account.name.set("Ada");
const json = JsonMapper.serialize(account, accountSchema);
const restored = JsonMapper.deserialize(json, accountSchema);
```

For class models the schema can be generated from TypeScript decorators. The
factory still creates the class, so deserialization returns a real instance:

```ts
import { JsonId, JsonMapper, JsonProperty } from "@anjunar/jfx-json";

class Profile {
  @JsonId id = "";
  @JsonProperty("displayName") name = "";
}

const profile = JsonMapper.deserialize({ id: "p-1", displayName: "Ada" }, Profile);
profile instanceof Profile; // true
const json = JsonMapper.serialize(profile); // schema is inferred from profile.constructor
```

Enable TypeScript's `experimentalDecorators` option when using the decorator
syntax (the option is already enabled in the demo and package test projects).
For decorated classes, `serialize(model)` infers the schema from the runtime
class and `deserialize(json, Profile)` accepts the class directly. Calling
`jsonSchema(Profile)` remains available for advanced schema composition and
polymorphic dependencies.

`jsonField` supports nested schemas (`schema`), array elements (`itemSchema`),
map values (`valueSchema`), JSON names, directional `serialize`/
`deserialize` flags and `id`; `jsonProperty`, `jsonIgnore` and `jsonId` are
convenience aliases for the corresponding Scala annotations. Property fields follow JFX dirty-payload
semantics: an unchanged `Property` is omitted, while an ID is retained. Plain
fields are always mapped. `JsonSchema.abstractType` and `typeName` provide the
`@type` discriminator used for polymorphic models.

The mapper has no runtime dependency on the Scala bridge. It only uses the
`Property` and `ListProperty` shape from `@anjunar/jfx-core`, declared as a peer
dependency so all packages in an application share the same JFX state runtime.

```bash
npm install @anjunar/jfx-json @anjunar/jfx-core
```

Run `npm run verify` in this package for typechecking, unit tests and a packed
consumer check.
