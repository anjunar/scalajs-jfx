import { button, classes, onClick } from "@anjunar/jfx-core";
import { notify } from "@anjunar/jfx-viewport";
import { translated } from "../../app/i18n.js";

export function viewportNotificationPage(): void {
  button(translated("Notify"), {}, () => {
    classes("px-3", "py-1.5");
    onClick(() => notify(translated("Saved.").get, { kind: "success" }));
  });
}
