import { classes, div } from "@anjunar/jfx-core";
import { routerLink } from "@anjunar/jfx-router";

export function routerLinksPage(): void {
  div(() => {
    classes("flex", "gap-3");
    routerLink("/", "Overview", { activeClass: "text-accent" });
    routerLink("/router/links", "This page", { activeClass: "text-accent" });
    routerLink("/no-such-route", "A broken link (still works without JS)");
  });
}
