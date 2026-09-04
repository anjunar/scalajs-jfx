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
import { div, heading, paragraph, text } from "@anjunar/jfx-core";
import { routerLink } from "@anjunar/jfx-router";
import type { PageBody, RouteContext } from "@anjunar/jfx-router";

export function routerParamsDetailLoad(context: RouteContext): Promise<PageBody> {
  return new Promise((resolve) => {
    setTimeout(() => {
      resolve(() => {
        div(() => {
          heading(3, () => text(`Loaded id: ${context.params.id}`));
          paragraph(() => text(`queryParams: ${JSON.stringify(context.queryParams)}`));
          paragraph(() => text(`failure: ${context.failure ?? "none"}`));
          routerLink("/router/params", "Back");
        });
      });
    }, 20);
  });
}
