import { classes, div, text } from "@anjunar/scalajs-ui-core";
import { routerLink, routerOutlet } from "@anjunar/scalajs-ui-router";
import { translated } from "../../app/i18n.js";

export function routerNestedPage(): void {
  div(() => {
    classes("flex", "flex-col", "gap-3");
    div(() => {
      text(translated("The panel below is rendered by a child route through routerOutlet()."));
    });
    routerLink("/router/nested/detail", translated("Open the nested panel"));
    routerOutlet();
  });
}
