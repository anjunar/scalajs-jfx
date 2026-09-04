/** Keep title/summary in sync with the "/viewport/overlay" entry in ../../app/catalog.ts. */
import { docPage } from "../../docs/page.js";
import { example } from "../../docs/example.js";
import { viewportOverlayPage } from "./page.js";
import snippet from "./page.ts?jfx-code";

export function viewportOverlayDoc(): void {
  docPage(
    { title: "Overlay", summary: "overlay(): a positioned floating layer -- the same primitive a combo box's dropdown is built from." },
    () => {
      example({ code: snippet }, () => {
        viewportOverlayPage();
      });
    }
  );
}
