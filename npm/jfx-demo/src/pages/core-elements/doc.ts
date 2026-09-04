/** Keep title/summary in sync with the "/core/elements" entry in ../../app/catalog.ts. */
import { docPage } from "../../docs/page.js";
import { example } from "../../docs/example.js";
import { coreElementsPage } from "./page.js";
import snippet from "./page.ts?jfx-code";

export function coreElementsDoc(): void {
  docPage(
    {
      title: "Extending the DSL",
      summary: "element() builds a tag wrapper in one line; attr/style/domProperty/on/addClass/self are the settings every wrapper is made from.",
    },
    () => {
      example({ code: snippet }, () => {
        coreElementsPage();
      });
    }
  );
}
