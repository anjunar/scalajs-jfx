import { classes, div } from "@anjunar/jfx-core";
import { routerLink } from "@anjunar/jfx-router";
import { translated } from "../../app/i18n.js";

export function routerLinksPage(): void {
  div(() => {
    classes("flex", "gap-3");
    routerLink("/", translated("Overview"), { activeClass: "text-accent" });
    routerLink("/router/links", translated("This page"), { activeClass: "text-accent" });
    routerLink("/no-such-route", translated("A broken link (still works without JS)"));
  });
}
