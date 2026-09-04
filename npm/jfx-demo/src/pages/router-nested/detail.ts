import { classes, div, text } from "@anjunar/jfx-core";
import { routerLink } from "@anjunar/jfx-router";

export function routerNestedDetailPage(): void {
  div(() => {
    classes("flex", "flex-col", "gap-2", "border", "border-line", "rounded-panel", "p-4");
    div(() => {
      classes("font-semibold");
      text("Nested panel");
    });
    div(() => {
      text("Reached at /router/nested/detail. The parent frame around it did not reload.");
    });
    routerLink("/router/nested", "Back");
  });
}
