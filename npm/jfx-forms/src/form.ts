/**
 * The root of a model-bound form. Mirrors `jfx.forms.Form`, minus the
 * `ClassDescriptor` a TypeScript model cannot have -- see `FormFactories.scala`'s
 * file-level doc comment for the substitute binding strategy: a control's
 * model property is found by name in `model` directly, and its validators
 * come from `schema` (see `validators.ts`) instead of case-class annotations.
 *
 * `model`'s values are the actual bridge `Property`/`ListProperty` handles
 * (from `runtime.property(...)`/`runtime.listProperty(...)`) -- not plain
 * values. A control registered under this form (`input`, `comboBox`, a nested
 * `subForm`, ...) is bound bidirectionally to whichever entry shares its name.
 */
import { component } from "@anjunar/jfx-core";
import type { FormHandle, ListProperty, Property } from "@anjunar/jfx-core";
import { defined } from "./internal.js";
import type { FormSchema } from "./validators.js";

/** A form model: one bridge `Property`/`ListProperty` per bindable field name. */
export type FormModel = Record<string, Property<unknown> | ListProperty<unknown>>;
export type { FormHandle };

export interface FormOptions {
  /** Distinguishes multiple forms in error responses / debugging. Defaults to `"default"`. */
  readonly name?: string;
  readonly schema?: FormSchema;
}

/** Mounts a form over `model`. `content` composes its controls with the core DSL. */
export function form<M extends FormModel>(
  model: M,
  options: FormOptions,
  content: () => void
): FormHandle {
  return component("form", defined({ model, ...options }), content) as FormHandle;
}
