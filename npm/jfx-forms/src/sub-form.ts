/**
 * A nested, dynamically bound `<fieldset>` that is itself a control of its
 * parent form -- participates in `validate()`/`clearErrors()`, cascades
 * `disabled`, and (unless `standalone`) binds bidirectionally to a `Property`
 * field of the enclosing model that holds the whole nested model. Mirrors
 * `jfx.forms.SubForm`.
 *
 * `model` is the nested form's *own*, unwrapped dictionary -- unless
 * `standalone`, the enclosing form binds to it through a same-named `Property`
 * field of its own model (the usual `input`-style name lookup, just one level
 * up), and that binding immediately overwrites `model`'s fields with whatever
 * the parent field currently holds -- so in the bound case `model` only needs
 * to be *a* dictionary of the right shape, not the live one.
 *
 * Not projected: `SubForm.newInstance()`/`clearForm()`/`factory` -- the
 * facade is reactive-input only, so mount a fresh `subForm` under `when()`
 * instead of reinstantiating one in place.
 */
import { component } from "@anjunar/jfx-core";
import { defined } from "./internal.js";
import { formSchemaOf } from "./validators.js";
import type { FormModel } from "./form.js";
import type { FormSchema } from "./validators.js";

export interface SubFormOptions {
  readonly schema?: FormSchema;
  /** Skips registration with the enclosing form context -- an independent nested model. */
  readonly standalone?: boolean;
}

/** Mounts a nested form named `name`, over `model`. */
export function subForm<M extends FormModel>(name: string, model: M, options: SubFormOptions, content: () => void): void;
export function subForm<M extends object>(name: string, model: M, content: () => void): void;
export function subForm<M extends object>(name: string, model: M, options: SubFormOptions, content: () => void): void;
export function subForm<M extends object>(
  name: string,
  model: M,
  optionsOrContent: SubFormOptions | (() => void),
  maybeContent?: () => void,
): void {
  const options = typeof optionsOrContent === "function" ? {} : optionsOrContent;
  const content = typeof optionsOrContent === "function" ? optionsOrContent : maybeContent;
  if (content === undefined) throw new TypeError("subForm requires a content callback");
  const schema = options.schema ?? formSchemaOf((model as { constructor: new () => object }).constructor);
  component("sub-form", defined({ name, model, ...options, schema }), content);
}
