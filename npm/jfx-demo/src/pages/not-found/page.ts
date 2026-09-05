import { classes, div, heading, paragraph, text } from "@anjunar/jfx-core";
import { routerLink } from "@anjunar/jfx-router";
import { translated } from "../../app/i18n.js";

export function notFoundPage(): void {
  div(() => {
    classes("not-found-page");
    heading(1, () => text(translated("404 — no such page")));
    paragraph(() => text(translated("This route is not in the table. The response carries status 404.")));
    routerLink("/", translated("Back to the overview"));
  });
}
