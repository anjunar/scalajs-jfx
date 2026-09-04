import { classes, div, paragraph, text } from "@anjunar/jfx-core";
import { routerLink, routerOutlet } from "@anjunar/jfx-router";

export function routerParamsPage(): void {
  div(() => {
    classes("flex", "flex-col", "gap-3");
    paragraph(() => {
      text("Both links below hit /router/params/:id. Only one matches its digits-only constraint -- the other falls back to onFailure, i.e. /404.");
    });
    div(() => {
      classes("flex", "gap-3");
      routerLink("/router/params/42", "Valid: /router/params/42");
      routerLink("/router/params/abc", "Invalid: /router/params/abc");
    });
    routerOutlet();
  });
}
