/**
 * A repeating group of controls, one per item of a `ListProperty` field.
 * Mirrors `jfx.forms.ArrayForm`.
 *
 * Faithful to the Scala component, including a real limitation it has today:
 * binding runs *into* each item control when the list structurally changes
 * (insert/remove/replace re-renders that index with its current value), but a
 * plain edit inside an item control is not written back into the array --
 * `jfx.forms.Formular.FormSpec` only exercises the read direction, and
 * `ArrayForm.compose` only ever pushes a value into a freshly built item, never
 * reads one back out of it. This is not something this facade papers over.
 */
import { component } from "@anjunar/jfx-core";
import { defined, indexBody } from "./internal.js";

export interface ArrayFormOptions {
  readonly standalone?: boolean;
}

/**
 * Mounts a repeating group named `name`, bound to a `ListProperty` field of the
 * enclosing form. `itemRenderer` runs once per item, at index `index`; it must
 * mount exactly one *non-standalone* control (commonly
 * `input(\`${name}-${index}\`)`) -- self-registration with this arrayForm's
 * own context is how `FormFactories.arrayFormRenderer` (in `jfx-bridge`)
 * recovers what got mounted; a `standalone` control never registers, and the
 * renderer throws rather than silently rendering nothing.
 */
export function arrayForm(
  name: string,
  itemRenderer: (index: number) => void,
  options: ArrayFormOptions = {}
): void {
  component(
    "array-form",
    defined({ name, itemRenderer: indexBody(itemRenderer), ...options }),
    () => {}
  );
}
