/**
 * A text input, bound by name to the nearest enclosing `form`/`subForm`/
 * `fieldSet`/`arrayForm`. Mirrors `jfx.forms.Input`.
 */
import { component } from "@anjunar/jfx-core";
import type { Reactive } from "@anjunar/jfx-core";
import { defined } from "./internal.js";

export interface InputOptions {
  /** The HTML `type` attribute. Defaults to `"text"`. */
  readonly type?: string;
  readonly placeholder?: Reactive<string>;
  /** Skips registration with the enclosing form context -- an input with no model binding. */
  readonly standalone?: boolean;
}

/** Mounts an input named `name`. `content` composes anything nested inside it (rarely needed). */
export function input(name: string, options: InputOptions = {}, content: () => void = () => {}): void {
  component("input", defined({ name, ...options }), content);
}
