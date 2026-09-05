/**
 * The root of a model-bound form. Mirrors `jfx.forms.Form`, minus the
 * `ClassDescriptor` a TypeScript model cannot have -- see `FormFactories.scala`'s
 * file-level doc comment for the substitute binding strategy: a control's
 * model property is found by name in `model` directly. Decorated class models
 * provide validator metadata at runtime; plain records can still pass `schema`
 * explicitly (see `validators.ts`).
 *
 * `model`'s values are the actual bridge `Property`/`ListProperty` handles
 * (from `runtime.property(...)`/`runtime.listProperty(...)`) -- not plain
 * values. A control registered under this form (`input`, `comboBox`, a nested
 * `subForm`, ...) is bound bidirectionally to whichever entry shares its name.
 */
import { component } from "@anjunar/jfx-core";
import type { FormHandle, ListProperty, Property } from "@anjunar/jfx-core";
import { defined } from "./internal.js";
import { formSchemaOf } from "./validators.js";
import type { FormSchema } from "./validators.js";

/** A form model: one bridge `Property`/`ListProperty` per bindable field name. */
export type FormModel = Record<string, Property<unknown> | ListProperty<unknown>>;
export type { FormHandle };

export interface FormOptions {
  /** Distinguishes multiple forms in error responses / debugging. Defaults to `"default"`. */
  readonly name?: string;
  readonly schema?: FormSchema;
}

/** Mounts a decorated class model and infers its validator schema. */
export function form<M extends FormModel>(model: M, options: FormOptions, content: () => void): FormHandle;
export function form<M extends object>(model: M, content: () => void): FormHandle;
export function form<M extends object>(model: M, options: FormOptions, content: () => void): FormHandle;
export function form<M extends object>(
  model: M,
  optionsOrContent: FormOptions | (() => void),
  maybeContent?: () => void,
): FormHandle {
  const options = typeof optionsOrContent === "function" ? {} : optionsOrContent;
  const content = typeof optionsOrContent === "function" ? optionsOrContent : maybeContent;
  if (content === undefined) throw new TypeError("form requires a content callback");
  const schema = options.schema ?? formSchemaOf((model as { constructor: new () => object }).constructor);
  return component("form", defined({ model, ...options, schema }), content) as FormHandle;
}
