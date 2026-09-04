/** Keep title/summary in sync with the "/core/async" entry in ../../app/catalog.ts. */
import { text } from "@anjunar/jfx-core";
import { docPage } from "../../docs/page.js";
import { example } from "../../docs/example.js";
import { callout } from "../../docs/callout.js";
import { coreAsyncPage } from "./page.js";
import snippet from "./page.ts?jfx-code";

export function coreAsyncDoc(): void {
  docPage(
    {
      title: "fetchInto",
      summary: "Renders asynchronously loaded data in place: SSR waits for the promise, hydration tolerates it still being in flight.",
    },
    () => {
      example({ code: snippet }, () => {
        coreAsyncPage();
      });

      callout("library-bug", () => {
        text(
          "when() next to fetchInto() does not hydrate: renderToString only serializes the settled state, but the client's first pass re-evaluates the condition from scratch and expects a DOM node the server never sent. page.ts on this route sidesteps it by branching once, inside the loader, instead of next to it -- see the comment there. Not fixed here; it is a gap in Condition/fetchInto interaction in the library itself."
        );
      });
    }
  );
}
