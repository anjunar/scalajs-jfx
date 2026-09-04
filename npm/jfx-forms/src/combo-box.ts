/**
 * A single- or multi-select dropdown, bound by name like `input`. Mirrors
 * `jfx.forms.ComboBox`.
 *
 * Its dropdown is an `@anjunar/jfx-viewport` `overlay`, so it needs a
 * `viewport(...)` ancestor -- the same requirement `jfx.forms.ComboBox` has
 * on the Scala side.
 *
 * Not projected: `valueRenderer`, `footerRenderer`, `identityBy`,
 * `selectionText`, `dropdownWidth`/`dropdownHeight`/`rowHeight` -- each has an
 * obvious trigger to add later if a consumer needs it. Item identity defaults
 * to reference equality, `ComboBox`'s own Scala default.
 */
import { component } from "@anjunar/jfx-core";
import type { ListProperty, ReadOnlyProperty } from "@anjunar/jfx-core";
import { defined, itemSelectedBody } from "./internal.js";

export interface ComboBoxOptions<T> {
  readonly items: ListProperty<T> | readonly T[];
  readonly placeholder?: string;
  readonly multiSelect?: boolean;
  /** Renders an item's display text. Defaults to `String(item)`. */
  readonly converter?: (item: T) => string;
  /** Renders one dropdown row. Defaults to the converted text. */
  readonly itemRenderer?: (item: T, selected: ReadOnlyProperty<boolean>) => void;
  /** Skips registration with the enclosing form context -- a combo box with no model binding. */
  readonly standalone?: boolean;
}

/** Mounts a combo box named `name`. */
export function comboBox<T>(name: string, options: ComboBoxOptions<T>): void {
  const { items, placeholder, multiSelect, converter, itemRenderer, standalone } = options;
  component(
    "combo-box",
    defined({
      name,
      items,
      placeholder,
      multiSelect,
      converter,
      itemRenderer: itemRenderer ? itemSelectedBody(itemRenderer) : undefined,
      standalone,
    })
  );
}
