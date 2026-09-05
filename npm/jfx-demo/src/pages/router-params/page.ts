import { classes, div, paragraph, text } from "@anjunar/jfx-core";
import { routerLink, routerOutlet } from "@anjunar/jfx-router";
import { translated } from "../../app/i18n.js";

export function routerParamsPage(): void {
  div(() => {
    classes("flex", "flex-col", "gap-3");
    paragraph(() => {
      text(translated("Both links below hit /router/params/:id. Only one matches its digits-only constraint -- the other falls back to onFailure, i.e. /404."));
    });
    div(() => {
      classes("flex", "gap-3");
      routerLink("/router/params/42", translated("Valid: /router/params/42"));
      routerLink("/router/params/abc", translated("Invalid: /router/params/abc"));
    });
    routerOutlet();
  });
}
