/**
 * The child route mounted at /router/params/:id. Its loader is genuinely
 * asynchronous (`Promise<PageBody>`, not a function returning one) -- SSR
 * waits for it, hydration tolerates it still being in flight, the same
 * contract fetchInto() is built on (see /core/async). `context.params.id`
 * only exists because :id matched the digits-only constraint declared next
 * to this route in app/catalog.ts -- RouteMatcher never calls this loader
 * for a segment the constraint rejects; the router forwards to onFailure
 * instead.
 */
import { div, heading, i18n, named, paragraph, t, text } from "@anjunar/jfx-core";
import { routerLink } from "@anjunar/jfx-router";
import type { PageBody, RouteContext } from "@anjunar/jfx-router";
import { translated } from "../../app/i18n.js";

export function routerParamsDetailLoad(context: RouteContext): Promise<PageBody> {
  return new Promise((resolve) => {
    setTimeout(() => {
      resolve(() => {
        div(() => {
          heading(3, () => text(i18n`Loaded id: ${named("id", context.params.id)}`));
          paragraph(() => text(t(i18n`queryParams: ${named("params", JSON.stringify(context.queryParams))}`)));
          paragraph(() => text(t(i18n`failure: ${named("reason", context.failure ?? "none")}`)));
          routerLink("/router/params", translated("Back"));
        });
      });
    }, 20);
  });
}
