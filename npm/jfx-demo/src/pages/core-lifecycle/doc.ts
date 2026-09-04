/** Keep title/summary in sync with the "/core/lifecycle" entry in ../../app/catalog.ts. */
import { text } from "@anjunar/jfx-core";
import { docPage } from "../../docs/page.js";
import { example } from "../../docs/example.js";
import { callout } from "../../docs/callout.js";
import { coreLifecyclePage } from "./page.js";
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
        text(
          "capture()'s restore only replays correctly within the same render pass. Calling it from a button's onClick and restoring well after hydration has already settled throws \"Hydration fault: There is no further DOM node\" -- the captured cursor was tied to a hydration-time position that no longer exists once hydration is done. @anjunar/jfx-viewport's own window/notification factories hit this exact fault and route around it by mounting through Viewport.addWindow/notify directly instead of the call-site cursor -- see JAVASCRIPT_API.md's account of it; a plain page.ts has no equivalent escape hatch. Not fixed here -- it is a real gap between capture() and onClick() composing new content together."
        );
      });
    }
  );
}
