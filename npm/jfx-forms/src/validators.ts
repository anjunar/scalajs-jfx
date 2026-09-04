/**
 * The form model schema, as plain data.
 *
 * `jfx.forms.Form`/`SubForm` derive a control's validators from annotations a
 * Scala-3 macro reads off an actual case class field
 * (`ReflectMacros.reflectWithAccessors[M]`) -- there is no TypeScript
 * equivalent of that macro, and no case class behind a TS model to read
 * annotations from in the first place. But the *next* step,
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
 * Pass a `{ field: ValidatorSpec[] }` map as `form(model, { schema })`.
 */

export interface ValidatorSpec {
  readonly name: string;
  readonly parameters: Record<string, unknown>;
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

/** `{ field: ValidatorSpec[] }`, as `form`/`subForm`'s `schema` option. */
export type FormSchema = Record<string, readonly ValidatorSpec[]>;
