import { button, classes, onClick } from "@anjunar/scalajs-ui-core";
import { notify } from "@anjunar/scalajs-ui-viewport";
import { translated } from "../../app/i18n.js";

export function viewportNotificationPage(): void {
  button(translated("Notify"), {}, () => {
    classes("px-3", "py-1.5");
    onClick(() => notify(translated("Saved.").get, { kind: "success" }));
  });
}
