import type { ListProperty, Property } from "@anjunar/jfx-core";

export type JsonPrimitive = string | number | boolean | null;
export type JsonValue = JsonPrimitive | JsonValue[] | { readonly [key: string]: JsonValue };

export interface JsonField<T = unknown> {
  /** Name written to JSON. Defaults to the model field name. */
  readonly name?: string;
  readonly schema?: JsonSchema<T>;
  readonly itemSchema?: JsonSchema<unknown>;
  readonly valueSchema?: JsonSchema<unknown>;
  /** Whether this field participates in serialization/deserialization. */
  readonly serialize?: boolean;
  readonly deserialize?: boolean;
  /** Include an identifier even when a Property has no dirty payload. */
  readonly id?: boolean;
}

export interface JsonSchemaOptions<T> {
  readonly factory: () => T;
  readonly fields: Readonly<Record<string, JsonField>>;
  readonly typeName?: string;
  readonly dependencies?: readonly JsonSchema<unknown>[];
  readonly subtypes?: readonly JsonSchema<unknown>[];
  /** Optional runtime test used to choose a subtype during serialization. */
  readonly matches?: (value: unknown) => boolean;
}

export type JsonModelConstructor<T> = new (...args: never[]) => T;

const decoratedFields = new WeakMap<Function, Map<string, JsonField>>();
const decoratedTypes = new WeakMap<Function, string>();

function decorateField(target: object, propertyKey: string | symbol, field: JsonField): void {
  const constructor = (target as { constructor: Function }).constructor;
  const fields = decoratedFields.get(constructor) ?? new Map<string, JsonField>();
  fields.set(String(propertyKey), field);
  decoratedFields.set(constructor, fields);
}

/** TypeScript decorator equivalent of Scala's `JsonProperty`. */
export function JsonProperty(name: string): PropertyDecorator {
  return (target, propertyKey) => decorateField(target, propertyKey, { name });
}

/** TypeScript decorator equivalent of Scala's `JsonIgnore`. */
export function JsonIgnore(options: { readonly serialize?: boolean; readonly deserialize?: boolean } = {}): PropertyDecorator {
  return (target, propertyKey) => decorateField(target, propertyKey, {
    serialize: options.serialize ?? false,
    deserialize: options.deserialize ?? false,
  });
}

/** TypeScript decorator equivalent of Scala's `JsonId`. */
export function JsonId(target: object, propertyKey: string | symbol): void {
  decorateField(target, propertyKey, { id: true });
}

/** TypeScript decorator equivalent of Scala's `JsonType` discriminator. */
export function JsonType(name: string): ClassDecorator {
  return (target) => {
    decoratedTypes.set(target, name);
  };
}

function fieldsOf(model: JsonModelConstructor<unknown>): Readonly<Record<string, JsonField>> {
  const fields: Record<string, JsonField> = {};
  let prototype: object | null = model.prototype;
  while (prototype !== null && prototype !== Object.prototype) {
    const constructor = (prototype as { constructor: Function }).constructor;
    decoratedFields.get(constructor)?.forEach((field, name) => {
      if (!(name in fields)) fields[name] = field;
    });
    prototype = Object.getPrototypeOf(prototype) as object | null;
  }
  return fields;
}

function schemaOfModel<T>(model: JsonModelConstructor<T>): JsonSchema<T> {
  return JsonSchema.fromClass(model);
}

/** Runtime metadata for one TypeScript model. */
export class JsonSchema<T> {
  readonly factory: () => T;
  readonly fields: Readonly<Record<string, JsonField>>;
  readonly typeName: string | undefined;
  readonly dependencies: readonly JsonSchema<unknown>[];
  readonly subtypes: readonly JsonSchema<unknown>[];
  readonly matches: ((value: unknown) => boolean) | undefined;

  constructor(options: JsonSchemaOptions<T>) {
    this.factory = options.factory;
    this.fields = options.fields;
    this.typeName = options.typeName;
    this.dependencies = options.dependencies ?? [];
    this.subtypes = options.subtypes ?? [];
    this.matches = options.matches;
  }

  static fromClass<T>(model: JsonModelConstructor<T>, factory: () => T = () => new model()): JsonSchema<T> {
    const typeName = decoratedTypes.get(model);
    return new JsonSchema({
      factory,
      fields: fieldsOf(model),
      ...(typeName === undefined ? {} : { typeName }),
    });
  }

  withDependencies(...schemas: readonly JsonSchema<unknown>[]): JsonSchema<T> {
    const options = {
      factory: this.factory,
      fields: this.fields,
      dependencies: [...this.dependencies, ...schemas],
      subtypes: this.subtypes,
      ...(this.matches === undefined ? {} : { matches: this.matches }),
      ...(this.typeName === undefined ? {} : { typeName: this.typeName }),
    } satisfies JsonSchemaOptions<T>;
    return new JsonSchema(options);
  }

  static abstractType<T>(
    subtypes: readonly JsonSchema<unknown>[],
    options: Omit<JsonSchemaOptions<T>, "subtypes" | "factory"> & { readonly factory?: () => T },
  ): JsonSchema<T> {
    const factory = options.factory ?? (() => {
      throw new Error("An abstract JsonSchema cannot be instantiated without a matching subtype");
    });
    return new JsonSchema({ ...options, factory, subtypes });
  }
}

/** Convenience constructor matching `JsonSchema(() => value)` on Scala. */
export function jsonSchema<T>(
  factory: () => T,
  fields: Readonly<Record<string, JsonField>>,
  options?: Omit<JsonSchemaOptions<T>, "factory" | "fields">,
): JsonSchema<T>;

export function jsonSchema<T>(model: JsonModelConstructor<T>): JsonSchema<T>;
export function jsonSchema<T>(
  model: JsonModelConstructor<T>,
  options: Omit<JsonSchemaOptions<T>, "factory" | "fields"> & {
    readonly factory?: () => T;
    readonly fields?: Readonly<Record<string, JsonField>>;
  },
): JsonSchema<T>;
export function jsonSchema<T>(
  factoryOrModel: (() => T) | JsonModelConstructor<T>,
  fieldsOrOptions: Readonly<Record<string, JsonField>> | (Omit<JsonSchemaOptions<T>, "factory" | "fields"> & {
    readonly factory?: () => T;
    readonly fields?: Readonly<Record<string, JsonField>>;
  }) = {},
  options: Omit<JsonSchemaOptions<T>, "factory" | "fields"> = {},
): JsonSchema<T> {
  if ("prototype" in factoryOrModel) {
    const model = factoryOrModel as JsonModelConstructor<T>;
    const modelOptions = fieldsOrOptions as Omit<JsonSchemaOptions<T>, "factory" | "fields"> & {
      readonly factory?: () => T;
      readonly fields?: Readonly<Record<string, JsonField>>;
    };
    const factory = modelOptions.factory ?? (() => new model());
    const fields = modelOptions.fields ?? fieldsOf(model);
    const { factory: _factory, fields: _fields, ...rest } = modelOptions;
    const typeName = rest.typeName ?? decoratedTypes.get(model);
    return new JsonSchema({
      factory,
      fields,
      ...rest,
      ...(typeName === undefined ? {} : { typeName }),
    });
  }
  return new JsonSchema({
    factory: factoryOrModel as () => T,
    fields: fieldsOrOptions as Readonly<Record<string, JsonField>>,
    ...options,
  });
}

/** Creates a field descriptor while preserving the inferred nested schema type. */
export function jsonField<T = unknown>(options: JsonField<T> = {}): JsonField<T> {
  return options;
}

/** Alias for a Scala `JsonProperty` annotation. */
export function jsonProperty(name: string): JsonField {
  return { name };
}

/** Alias for a Scala `JsonIgnore` annotation. */
export function jsonIgnore(options: { readonly serialize?: boolean; readonly deserialize?: boolean } = {}): JsonField {
  return {
    serialize: options.serialize ?? false,
    deserialize: options.deserialize ?? false,
  };
}

/** Alias for a Scala `JsonId` annotation. */
export function jsonId(): JsonField {
  return { id: true };
}

type PropertyLike = Property<unknown>;
type ListPropertyLike = ListProperty<unknown>;

function isProperty(value: unknown): value is PropertyLike {
  return (
    value !== null &&
    typeof value === "object" &&
    "get" in value &&
    "isDirty" in value &&
    typeof (value as { set?: unknown }).set === "function"
  );
}

function isListProperty(value: unknown): value is ListPropertyLike {
  return (
    value !== null &&
    typeof value === "object" &&
    "get" in value &&
    typeof (value as { setAll?: unknown }).setAll === "function"
  );
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function encodeUnknown(value: unknown): JsonValue {
  if (value === null || value === undefined) return null;
  if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") return value;
  if (Array.isArray(value)) return value.map(encodeUnknown);
  if (isRecord(value)) {
    const result: Record<string, JsonValue> = {};
    for (const [key, item] of Object.entries(value)) result[key] = encodeUnknown(item);
    return result;
  }
  return String(value);
}

function schemaForValue(
  value: unknown,
  declared: JsonSchema<unknown>,
): JsonSchema<unknown> {
  if (declared.subtypes.length === 0) return declared;
  const constructorName = isRecord(value)
    ? (value.constructor as { name?: string } | undefined)?.name
    : undefined;
  const valueConstructor = isRecord(value) ? value.constructor : undefined;
  return declared.subtypes.find(
    (candidate) => candidate.matches?.(value) === true,
  ) ?? declared.subtypes.find(
    (candidate) => candidate.typeName !== undefined && candidate.typeName === constructorName,
  ) ?? declared.subtypes.find((candidate) => {
    if (candidate.typeName !== undefined && candidate.typeName === (value as { typeName?: string })?.typeName) return true;
    try {
      const prototype = candidate.factory();
      return isRecord(prototype) && prototype.constructor === valueConstructor;
    } catch {
      return false;
    }
  }) ?? declared;
}

function fieldValue(value: unknown): unknown {
  if (isProperty(value) || isListProperty(value)) return value.get;
  return value;
}

function hasDirtyPayload(value: unknown, field: JsonField): boolean {
  if (isProperty(value)) {
    const nested: JsonField = field.schema === undefined ? {} : { schema: field.schema };
    return value.isDirty || hasDirtyPayload(value.get, nested);
  }
  if (isListProperty(value)) {
    const nested: JsonField = field.itemSchema === undefined ? {} : { schema: field.itemSchema };
    // `ListProperty` deliberately exposes no `isDirty` in the JS contract. A
    // non-empty list is therefore the observable payload; nested schemas still
    // get their own dirty check below.
    return value.get.length > 0 || value.get.some((item) => hasDirtyPayload(item, nested));
  }
  if (field.schema && value !== null && value !== undefined) {
    return Object.entries(field.schema.fields).some(([key, child]) => {
      const current = isRecord(value) ? value[key] : undefined;
      return child.id !== true && shouldSerialize(current, child);
    });
  }
  return false;
}

function shouldSerialize(value: unknown, field: JsonField): boolean {
  if (field.id === true) return fieldValue(value) !== null && fieldValue(value) !== undefined;
  if (isProperty(value) || isListProperty(value)) return hasDirtyPayload(value, field);
  return true;
}

export class JsonMapper {
  private readonly schemas: readonly JsonSchema<unknown>[];

  constructor(...schemas: readonly JsonSchema<unknown>[]) {
    this.schemas = schemas;
  }

  serialize<T>(model: T, schema?: JsonSchema<T>): JsonValue {
    if (schema === undefined && (model === null || model === undefined)) return null;
    const resolved = schema ?? schemaOfModel((model as { constructor: JsonModelConstructor<T> }).constructor);
    return this.encode(model, resolved as JsonSchema<unknown>);
  }

  deserialize<T>(json: unknown, schemaOrModel: JsonSchema<T> | JsonModelConstructor<T>): T {
    const schema = schemaOrModel instanceof JsonSchema ? schemaOrModel : schemaOfModel(schemaOrModel);
    return this.decode(json, schema as JsonSchema<unknown>) as T;
  }

  deserializeArray<T>(json: unknown, schemaOrModel: JsonSchema<T> | JsonModelConstructor<T>): T[] {
    if (json === null || json === undefined) return [];
    if (!Array.isArray(json)) throw new TypeError("Expected JSON array");
    return json.map((item) => this.deserialize(item, schemaOrModel));
  }

  static serialize<T>(model: T, schema?: JsonSchema<T>): JsonValue {
    return new JsonMapper().serialize(model, schema);
  }

  static deserialize<T>(json: unknown, schemaOrModel: JsonSchema<T> | JsonModelConstructor<T>): T {
    return new JsonMapper().deserialize(json, schemaOrModel);
  }

  static deserializeArray<T>(json: unknown, schemaOrModel: JsonSchema<T> | JsonModelConstructor<T>): T[] {
    return new JsonMapper().deserializeArray(json, schemaOrModel);
  }

  private encode(value: unknown, schema: JsonSchema<unknown>): JsonValue {
    if (value === null || value === undefined) return null;
    if (isProperty(value) || isListProperty(value)) return this.encode(fieldValue(value), schema);
    if (!isRecord(value)) return encodeUnknown(value);

    const selected = schemaForValue(value, schema);
    const result: Record<string, JsonValue> = {};
    if (selected.typeName !== undefined) result["@type"] = selected.typeName;
    for (const [key, field] of Object.entries(selected.fields)) {
      if (field.serialize === false) continue;
      const current = value[key];
      if (!shouldSerialize(current, field)) continue;
      const raw = fieldValue(current);
      result[field.name ?? key] = field.schema
        ? this.encode(raw, field.schema as JsonSchema<unknown>)
        : field.itemSchema && Array.isArray(raw)
          ? raw.map((item) => this.encode(item, field.itemSchema!))
          : field.valueSchema && isRecord(raw)
            ? Object.fromEntries(Object.entries(raw).map(([entryKey, item]) => [entryKey, this.encode(item, field.valueSchema!)]))
            : encodeUnknown(raw);
    }
    return result;
  }

  private decode(value: unknown, schema: JsonSchema<unknown>): unknown {
    if (value === null || value === undefined) return null;
    if (!isRecord(value)) throw new TypeError("Expected JSON object");
    const candidates = [schema, ...schema.subtypes, ...this.schemas.flatMap((root) => [root, ...root.subtypes])];
    let selected = schema;
    if (schema.subtypes.length > 0) {
      const typeName = value["@type"];
      if (typeof typeName !== "string") {
        throw new Error("Missing @type for abstract JsonSchema");
      }
      selected = candidates.find((candidate) => candidate.typeName === typeName) ?? (() => {
        throw new Error(`Unknown @type '${typeName}'`);
      })();
    } else if ("@type" in value && schema.typeName !== value["@type"]) {
      throw new Error(`Unknown @type '${String(value["@type"])}'`);
    }
    const instance = selected.factory();
    for (const [key, field] of Object.entries(selected.fields)) {
      if (field.deserialize === false) continue;
      const jsonKey = field.name ?? key;
      if (!(jsonKey in value)) continue;
      const mapped = field.schema
        ? this.decode(value[jsonKey], field.schema as JsonSchema<unknown>)
        : field.itemSchema && Array.isArray(value[jsonKey])
          ? value[jsonKey].map((item) => this.decode(item, field.itemSchema!))
          : field.valueSchema && isRecord(value[jsonKey])
            ? Object.fromEntries(Object.entries(value[jsonKey]).map(([entryKey, item]) => [entryKey, this.decode(item, field.valueSchema!)]))
            : value[jsonKey];
      const target = instance as Record<string, unknown>;
      const current = target[key];
      if (isProperty(current)) {
        current.set(mapped);
        const setDefault = (current as unknown as { setDefault?: (value: unknown) => void }).setDefault;
        setDefault?.(mapped);
      } else if (isListProperty(current)) {
        current.setAll(Array.isArray(mapped) ? mapped : []);
      } else {
        target[key] = mapped;
      }
    }
    return instance;
  }
}

/** Stateless convenience functions for callers that do not need a mapper catalog. */
export function serialize<T>(model: T, schema?: JsonSchema<T>): JsonValue {
  return JsonMapper.serialize(model, schema);
}

export function deserialize<T>(json: unknown, schemaOrModel: JsonSchema<T> | JsonModelConstructor<T>): T {
  return JsonMapper.deserialize(json, schemaOrModel);
}

export function deserializeArray<T>(json: unknown, schemaOrModel: JsonSchema<T> | JsonModelConstructor<T>): T[] {
  return JsonMapper.deserializeArray(json, schemaOrModel);
}
