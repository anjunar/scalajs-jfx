/**
 * A floating-label wrapper around one control: label, divider, and an error
 * list, all driven by the control it wraps (empty/focus/dirty/invalid state
 * classes, the control's own `errors`). Mirrors `ui.forms.InputContainer`.
 */
import { component } from "@anjunar/scalajs-ui-core";
import type { Reactive } from "@anjunar/scalajs-ui-core";
import { defined } from "./internal.js";

export interface InputContainerOptions {
  readonly label: Reactive<string>;
}

/** Mounts a label wrapper. `content` should mount exactly one control (`input`, `comboBox`, ...). */
export function inputContainer(options: InputContainerOptions, content: () => void): void {
  component("input-container", defined({ ...options }), content);
}
