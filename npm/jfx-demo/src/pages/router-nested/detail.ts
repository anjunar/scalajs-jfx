import { classes, div, text } from "@anjunar/jfx-core";
import { routerLink } from "@anjunar/jfx-router";
import { translated } from "../../app/i18n.js";

export function routerNestedDetailPage(): void {
  div(() => {
    classes("flex", "flex-col", "gap-2", "border", "border-line", "rounded-panel", "p-4");
    div(() => {
      classes("font-semibold");
      text(translated("Nested panel"));
    });
    div(() => {
      text(translated("Reached at /router/nested/detail. The parent frame around it did not reload."));
    });
    routerLink("/router/nested", translated("Back"));
  });
}
