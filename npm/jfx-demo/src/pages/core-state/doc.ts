/** Keep title/summary in sync with the "/core/state" entry in ../../app/catalog.ts. */
import { docPage } from "../../docs/page.js";
import { example } from "../../docs/example.js";
import { coreStatePage } from "./page.js";
import snippet from "./page.ts?jfx-code";

export function coreStateDoc(): void {
  docPage(
    {
      title: "Property",
      summary: "A Property<T> holds a value and notifies on change; text() renders it, and a derived Property re-renders when its source does.",
    },
    () => {
      example({ code: snippet }, () => {
        coreStatePage();
      });
    }
  );
}
