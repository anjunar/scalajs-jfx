/** Keep title/summary in sync with the "/core/lifecycle" entry in ../../app/catalog.ts. */
import { text } from "@anjunar/jfx-core";
import { docPage } from "../../docs/page.js";
import { example } from "../../docs/example.js";
import { callout } from "../../docs/callout.js";
import { coreLifecyclePage } from "./page.js";
import { translated } from "../../app/i18n.js";
import snippet from "./page.ts?jfx-code";

export function coreLifecycleDoc(): void {
  docPage(
    {
      title: "Lifetime and hydration",
      summary: "isBrowser()/isHydrating() report the environment; capture() lets composition resume later, which is why a render body must be synchronous.",
    },
    () => {
      example({ code: snippet }, () => {
        coreLifecyclePage();
      });

      callout("library-bug", () => {
        text(translated(
          "capture() keeps the component position and recreates a live append cursor for later callbacks. Calling the restore from a button's onClick after hydration is therefore safe; the consumed hydration cursor is never reused. It still does not make SSR wait -- use fetchInto() when the result must be present in the HTML."
        ));
      });
    }
  );
}
