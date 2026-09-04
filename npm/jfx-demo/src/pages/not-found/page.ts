import { classes, div, heading, paragraph, text } from "@anjunar/jfx-core";
import { routerLink } from "@anjunar/jfx-router";

export function notFoundPage(): void {
  div(() => {
    classes("not-found-page");
    heading(1, () => text("404 — no such page"));
    paragraph(() => text("This route is not in the table. The response carries status 404."));
    routerLink("/", "Back to the overview");
  });
}
