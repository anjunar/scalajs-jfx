/** Keep title/summary in sync with the "/viewport/window" entry in ../../app/catalog.ts. */
import { docPage } from "../../docs/page.js";
import { example } from "../../docs/example.js";
import { viewportWindowPage } from "./page.js";
import snippet from "./page.ts?jfx-code";

export function viewportWindowDoc(): void {
  docPage(
    { title: "Window", summary: "floatingWindow(): a draggable panel mounted above the routed page, closed through onClose." },
    () => {
      example({ code: snippet }, () => {
        viewportWindowPage();
      });
    }
  );
}
