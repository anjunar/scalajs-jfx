/**
 * The form model schema, as plain data.
 *
 * `jfx.forms.Form`/`SubForm` derive a control's validators from annotations a
 * Scala-3 macro reads off an actual case class field
 * (`ReflectMacros.reflectWithAccessors[M]`). Decorated TypeScript classes carry
 * equivalent metadata in a WeakMap and `form`/`subForm` turn it into the same
 * runtime schema. Plain records can still pass a schema explicitly. The next step,
 * `ValidatorFactory.createValidator`, already dispatches on plain runtime data
 * -- `reflect.Annotation(annotationClassName, parameters)` -- not on anything
 * macro-built. A {@link ValidatorSpec} here is exactly that shape, spelled as
 * a function call instead of an annotation: `notNull()` becomes
 * `{ name: "jfx.forms.validators.NotNull", parameters: {} }`, and
 * `FormFactories.schemaFrom` (in `jfx-bridge`) turns it into a real
 * `Annotation` that the same, unmodified `ValidatorFactory`/
 * `BuiltinValidators` consume. No validator logic is ported here -- this file
 * only builds the data the existing Scala dispatch table already reads.
 *
 * Pass a `{ field: ValidatorSpec[] }` map as `form(model, { schema })` when
 * using a plain record or when overriding decorated metadata.
 */

export interface ValidatorSpec {
  readonly name: string;
  readonly parameters: Record<string, unknown>;
}

type FormModelConstructor = new (...args: never[]) => object;
const decoratedValidators = new WeakMap<Function, Map<string, ValidatorSpec[]>>();

function decorateValidator(target: object, propertyKey: string | symbol, validators: readonly ValidatorSpec[]): void {
  const constructor = (target as { constructor: Function }).constructor;
  const fields = decoratedValidators.get(constructor) ?? new Map<string, ValidatorSpec[]>();
  const current = fields.get(String(propertyKey)) ?? [];
  fields.set(String(propertyKey), [...current, ...validators]);
  decoratedValidators.set(constructor, fields);
}

/** Adds validator metadata to a form model field. */
export function FormValidator(...validators: readonly ValidatorSpec[]): PropertyDecorator {
  return (target, propertyKey) => decorateValidator(target, propertyKey, validators);
}

/** Reads validator metadata from a decorated model class. */
export function formSchemaOf(model: FormModelConstructor): FormSchema {
  const schema: FormSchema = {};
  let prototype: object | null = model.prototype;
  while (prototype !== null && prototype !== Object.prototype) {
    const constructor = (prototype as { constructor: Function }).constructor;
    decoratedValidators.get(constructor)?.forEach((validators, name) => {
      if (!(name in schema)) schema[name] = validators;
    });
    prototype = Object.getPrototypeOf(prototype) as object | null;
  }
  return schema;
}

function spec(name: string, parameters: Record<string, unknown> = {}): ValidatorSpec {
  const out: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(parameters)) {
    if (value !== undefined) out[key] = value;
  }
  return { name: `jfx.forms.validators.${name}`, parameters: out };
}

export const notNull = (message?: string): ValidatorSpec => spec("NotNull", { message });
export const isNull = (message?: string): ValidatorSpec => spec("Null", { message });
export const assertTrue = (message?: string): ValidatorSpec => spec("AssertTrue", { message });
export const assertFalse = (message?: string): ValidatorSpec => spec("AssertFalse", { message });
export const notEmpty = (message?: string): ValidatorSpec => spec("NotEmpty", { message });
export const notBlank = (message?: string): ValidatorSpec => spec("NotBlank", { message });

export const size = (min?: number, max?: number, message?: string): ValidatorSpec =>
  spec("Size", { min, max, message });

export const min = (value: number, message?: string): ValidatorSpec =>
  spec("Min", { value, message });

export const max = (value: number, message?: string): ValidatorSpec =>
  spec("Max", { value, message });

export const decimalMin = (value: string, inclusive?: boolean, message?: string): ValidatorSpec =>
  spec("DecimalMin", { value, inclusive, message });

export const decimalMax = (value: string, inclusive?: boolean, message?: string): ValidatorSpec =>
  spec("DecimalMax", { value, inclusive, message });

export const positive = (message?: string): ValidatorSpec => spec("Positive", { message });
export const positiveOrZero = (message?: string): ValidatorSpec =>
  spec("PositiveOrZero", { message });
export const negative = (message?: string): ValidatorSpec => spec("Negative", { message });
export const negativeOrZero = (message?: string): ValidatorSpec =>
  spec("NegativeOrZero", { message });

export const digits = (integer: number, fraction: number, message?: string): ValidatorSpec =>
  spec("Digits", { integer, fraction, message });

/** Java regex syntax (the Scala side compiles it with `scala.util.matching.Regex`). A `RegExp`'s
 * `.source` is close enough for the common cases; write the pattern as a string for anything more
 * exotic.
 */
export const pattern = (regex: string | RegExp, message?: string): ValidatorSpec =>
  spec("Pattern", { regex: typeof regex === "string" ? regex : regex.source, message });

export const email = (message?: string): ValidatorSpec => spec("EmailConstraint", { message });

export const past = (message?: string): ValidatorSpec => spec("Past", { message });
export const pastOrPresent = (message?: string): ValidatorSpec => spec("PastOrPresent", { message });
export const future = (message?: string): ValidatorSpec => spec("Future", { message });
export const futureOrPresent = (message?: string): ValidatorSpec =>
  spec("FutureOrPresent", { message });

/** TypeScript decorator equivalents of the Scala validator annotations. */
export const NotNull = (message?: string): PropertyDecorator => FormValidator(notNull(message));
export const Null = (message?: string): PropertyDecorator => FormValidator(isNull(message));
export const AssertTrue = (message?: string): PropertyDecorator => FormValidator(assertTrue(message));
export const AssertFalse = (message?: string): PropertyDecorator => FormValidator(assertFalse(message));
export const NotEmpty = (message?: string): PropertyDecorator => FormValidator(notEmpty(message));
export const NotBlank = (message?: string): PropertyDecorator => FormValidator(notBlank(message));
export const Size = (minValue?: number, maxValue?: number, message?: string): PropertyDecorator =>
  FormValidator(size(minValue, maxValue, message));
export const Min = (value: number, message?: string): PropertyDecorator => FormValidator(min(value, message));
export const Max = (value: number, message?: string): PropertyDecorator => FormValidator(max(value, message));
export const DecimalMin = (value: string, inclusive?: boolean, message?: string): PropertyDecorator =>
  FormValidator(decimalMin(value, inclusive, message));
export const DecimalMax = (value: string, inclusive?: boolean, message?: string): PropertyDecorator =>
  FormValidator(decimalMax(value, inclusive, message));
export const Positive = (message?: string): PropertyDecorator => FormValidator(positive(message));
export const PositiveOrZero = (message?: string): PropertyDecorator => FormValidator(positiveOrZero(message));
export const Negative = (message?: string): PropertyDecorator => FormValidator(negative(message));
export const NegativeOrZero = (message?: string): PropertyDecorator => FormValidator(negativeOrZero(message));
export const Digits = (integer: number, fraction: number, message?: string): PropertyDecorator =>
  FormValidator(digits(integer, fraction, message));
export const Pattern = (regex: string | RegExp, message?: string): PropertyDecorator =>
  FormValidator(pattern(regex, message));
export const Email = (message?: string): PropertyDecorator => FormValidator(email(message));
export const Past = (message?: string): PropertyDecorator => FormValidator(past(message));
export const PastOrPresent = (message?: string): PropertyDecorator => FormValidator(pastOrPresent(message));
export const Future = (message?: string): PropertyDecorator => FormValidator(future(message));
export const FutureOrPresent = (message?: string): PropertyDecorator => FormValidator(futureOrPresent(message));

/** `{ field: ValidatorSpec[] }`, as `form`/`subForm`'s `schema` option. */
export type FormSchema = Record<string, readonly ValidatorSpec[]>;
