import { installRuntime, property, listProperty } from "@anjunar/jfx-core";
import { stubRuntime } from "@anjunar/jfx-core/stub";
import { JsonId, JsonMapper, JsonProperty, JsonSchema, JsonType, jsonField, jsonSchema } from "../src/index.js";
import { describe, expect, it } from "vitest";

installRuntime(stubRuntime);

interface Address { city: string; }
interface Person { id: ReturnType<typeof property<string>>; name: ReturnType<typeof property<string>>; address: Address; tags: ReturnType<typeof listProperty<string>>; secret: ReturnType<typeof property<string>>; }

const addressSchema = jsonSchema<Address>(() => ({ city: "" }), { city: jsonField() });
const personSchema = jsonSchema<Person>(
  () => ({ id: property(""), name: property(""), address: { city: "" }, tags: listProperty<string>([]), secret: property("") }),
  {
    id: jsonField({ id: true }),
    name: jsonField({ name: "fullName" }),
    address: jsonField({ schema: addressSchema }),
    tags: jsonField(),
    secret: jsonField({ serialize: false, deserialize: false }),
  },
);

describe("JsonMapper", () => {
  it("builds a schema from TypeScript property decorators", () => {
    @JsonType("profile")
    class DecoratedProfile {
      @JsonId id = property("");
      @JsonProperty("displayName") name = property("");
    }
    const schema = jsonSchema(DecoratedProfile);
    const profile = JsonMapper.deserialize({ id: "p-1", displayName: "Ada" }, schema);
    expect(profile).toBeInstanceOf(DecoratedProfile);
    expect(profile.id.get).toBe("p-1");
    expect(JsonMapper.serialize(profile, schema)).toEqual({ "@type": "profile", id: "p-1", displayName: "Ada" });
  });

  it("infers decorated class schemas at the mapper boundary", () => {
    @JsonType("profile")
    class InferredProfile {
      @JsonId id = property("");
      @JsonProperty("displayName") name = property("");
    }

    const profile = JsonMapper.deserialize({ id: "p-2", displayName: "Grace" }, InferredProfile);
    expect(profile).toBeInstanceOf(InferredProfile);
    profile.name.set("Ada");
    expect(JsonMapper.serialize(profile)).toEqual({ "@type": "profile", id: "p-2", displayName: "Ada" });
    expect(JsonMapper.deserializeArray([{ id: "p-3", displayName: "Lin" }], InferredProfile)[0]).toBeInstanceOf(InferredProfile);
  });

  it("maps renamed fields and nested schemas", () => {
    const model = personSchema.factory();
    model.id.set("p-1");
    model.name.set("Ada");
    model.address.city = "London";
    model.tags.add("admin");
    const json = JsonMapper.serialize(model, personSchema);
    expect(json).toEqual({ id: "p-1", fullName: "Ada", address: { city: "London" }, tags: ["admin"] });

    const restored = JsonMapper.deserialize({ id: "p-2", fullName: "Grace", address: { city: "Paris" }, tags: ["staff"] }, personSchema);
    expect(restored.id.get).toBe("p-2");
    expect(restored.name.get).toBe("Grace");
    expect(restored.secret.get).toBe("");
    expect(restored.tags.get).toEqual(["staff"]);
  });

  it("supports polymorphic schemas and null arrays", () => {
    class Circle { radius = 0; }
    class Square { size = 0; }
    const circle = jsonSchema(() => new Circle(), { radius: jsonField() }, { typeName: "circle" });
    const square = jsonSchema(() => new Square(), { size: jsonField() }, { typeName: "square" });
    const shape = JsonSchema.abstractType([circle, square], { factory: () => ({}) as Circle | Square, fields: {} });
    expect(JsonMapper.serialize(new Circle(), shape)).toEqual({ "@type": "circle", radius: 0 });
    expect(JsonMapper.deserialize({ "@type": "square", size: 4 }, shape)).toBeInstanceOf(Square);
    expect(JsonMapper.deserializeArray(null, circle)).toEqual([]);
  });
});
