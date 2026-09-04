/**
 * A `<fieldset>` that groups controls for error propagation, disabled-state
 * cascading, and a dotted error-path prefix -- not for binding *its children*
 * to the model. Mirrors `jfx.forms.FieldSet`: like its Scala counterpart, a
 * control registered inside a `fieldSet` is *not* bound to any model property
 * by name (only `form`/`subForm` bind); use it to group already-bound or
 * `standalone` controls.
 *
 * The `fieldSet` itself, though, *is* registered as a control of its own
 * enclosing form -- same as `jfx.forms.FieldSet <: Control[Unit]` -- so
 * mounting one under a `form`/`subForm` whose model has no same-named field
 * logs a binding-failure message (harmless: the group still renders and
 * groups). Give it a distinct `name` from any real model field, or mount it
 * only under another `fieldSet`.
 */
import { component } from "@anjunar/jfx-core";
import { defined } from "./internal.js";

export interface FieldSetOptions {
  readonly name: string;
}

export function fieldSet(options: FieldSetOptions, content: () => void): void {
  component("field-set", defined({ ...options }), content);
}
