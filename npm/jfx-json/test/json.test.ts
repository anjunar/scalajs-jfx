import { installRuntime, property, listProperty } from "@anjunar/jfx-core";
import { stubRuntime } from "@anjunar/jfx-core/stub";
import { JsonId, JsonIgnore, JsonMapper, JsonProperty, JsonSchema, JsonType, jsonField, jsonSchema } from "../src/index.js";
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

  it("merges inherited decorators, stacked field metadata, and explicit nested fields", () => {
    class Base {
      @JsonId id = property("");
      @JsonProperty("$links")
      @JsonIgnore({ serialize: false, deserialize: true })
      links = listProperty<string>([]);
    }
    class Nested { @JsonProperty("label") label = property(""); }
    class Model extends Base {
      @JsonProperty("displayName") name = property("");
      nested = property(new Nested());
    }
    const nestedSchema = jsonSchema(Nested);
    const schema = jsonSchema(Model, {
      fields: {
        links: jsonField(),
        nested: jsonField({ schema: nestedSchema }),
      },
    });
    const model = JsonMapper.deserialize(
      { "@type": "server-model", id: "m-1", displayName: "Ada", nested: { label: "child" }, $links: ["self"] },
      schema,
    );
    expect(model.name.get).toBe("Ada");
    expect(model.nested.get).toBeInstanceOf(Nested);
    expect(model.links.get).toEqual(["self"]);
    model.name.set("Grace");
    expect(JsonMapper.serialize(model, schema)).toEqual({
      id: "m-1",
      displayName: "Grace",
      nested: { label: "child" },
    });
  });

  it("resolves lazy decorator model providers for nested properties and lists", () => {
    let calls = 0;
    class Child {
      @JsonProperty() label = property("");
    }
    const childModel = () => {
      calls += 1;
      return Child;
    };
    class Parent {
      @JsonProperty(childModel) nested = property<Child | null>(null);
      @JsonProperty("children", childModel) children = listProperty<Child>([]);
    }

    const schema = jsonSchema(Parent);
    expect(calls).toBe(0);
    const restored = JsonMapper.deserialize({ nested: { label: "Ada" }, children: [{ label: "Grace" }] }, schema);
    expect(calls).toBe(1);

    restored.nested.get!.label.set("Lin");
    restored.children.add(new Child());
    restored.children.get[1]!.label.set("Rosa");
    expect(JsonMapper.serialize(restored, schema)).toEqual({
      nested: { label: "Lin" },
      children: [{ label: "Grace" }, { label: "Rosa" }],
    });
  });

  it("inherits nested model providers across class hierarchies", () => {
    class Child {
      @JsonProperty("title") title = property("");
    }
    class Base {
      @JsonProperty(() => Child) fromBase = property<Child | null>(null);
    }
    class Model extends Base {
      @JsonProperty(() => Child) fromModel = property<Child | null>(null);
    }

    const schema = jsonSchema(Model);
    const restored = JsonMapper.deserialize({ fromBase: { title: "Base" }, fromModel: { title: "Model" } }, schema);
    expect(restored.fromBase.get).toBeInstanceOf(Child);
    expect(restored.fromModel.get).toBeInstanceOf(Child);
    restored.fromModel.get!.title.set("Updated");
    expect(JsonMapper.serialize(restored, schema)).toEqual({
      fromBase: { title: "Base" },
      fromModel: { title: "Updated" },
    });
  });

  it("keeps explicit schema fields as highest priority over decorator models", () => {
    class Child {
      @JsonProperty() value = property("");
    }
    class Alternate {
      @JsonProperty() label = property("");
    }
    class Model {
      @JsonProperty("payload", () => Child) payload = property<Child | null>(null);
    }

    const alternateSchema = jsonSchema(Alternate);
    const schema = jsonSchema(Model, {
      fields: {
        payload: jsonField({ schema: alternateSchema }),
      },
    });
    const model = JsonMapper.deserialize({ payload: { label: "Explicit" } }, schema);
    expect(model.payload.get).toBeInstanceOf(Alternate);
    expect(JsonMapper.serialize(model, schema)).toEqual({ payload: { label: "Explicit" } });
  });

  it("invokes custom property defaults with their owner", () => {
    class OwnerAwareProperty {
      value = "";
      defaultValue = "";
      get get(): string { return this.value; }
      get isDirty(): boolean { return this.value !== this.defaultValue; }
      set(value: string): void { this.value = value; }
      setDefault(value: string): void { this.defaultValue = value; }
    }
    class Model { @JsonProperty("value") value = new OwnerAwareProperty(); }

    const model = JsonMapper.deserialize({ value: "hydrated" }, Model);
    expect(model.value.get).toBe("hydrated");
    expect(model.value.isDirty).toBe(false);
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
