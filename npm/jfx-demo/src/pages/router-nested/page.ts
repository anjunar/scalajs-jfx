import { classes, div, text } from "@anjunar/jfx-core";
import { routerLink, routerOutlet } from "@anjunar/jfx-router";

export function routerNestedPage(): void {
  div(() => {
    classes("flex", "flex-col", "gap-3");
    div(() => {
      text("The panel below is rendered by a child route through routerOutlet().");
    });
    routerLink("/router/nested/detail", "Open the nested panel");
    routerOutlet();
  });
}
