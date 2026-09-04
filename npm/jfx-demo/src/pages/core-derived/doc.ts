/** Keep title/summary in sync with the "/core/derived" entry in ../../app/catalog.ts. */
import { docPage } from "../../docs/page.js";
import { example } from "../../docs/example.js";
import { coreDerivedPage } from "./page.js";
import snippet from "./page.ts?jfx-code";

export function coreDerivedDoc(): void {
  docPage(
    {
      title: "Derived state",
      summary: "map() derives one Property from another; observe()/observeWithoutInitial() run a side effect on every change, disposeWith() ties it to this page's lifetime.",
    },
    () => {
      example({ code: snippet }, () => {
        coreDerivedPage();
      });
    }
  );
}
