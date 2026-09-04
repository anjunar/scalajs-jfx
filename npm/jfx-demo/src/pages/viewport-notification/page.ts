import { button, classes, onClick } from "@anjunar/jfx-core";
import { notify } from "@anjunar/jfx-viewport";

export function viewportNotificationPage(): void {
  button("Notify", {}, () => {
    classes("px-3", "py-1.5");
    onClick(() => notify("Saved.", { kind: "success" }));
  });
}
