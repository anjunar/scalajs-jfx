/** Keep title/summary in sync with the "/controls/tabs" entry in ../../app/catalog.ts. */
import { docPage } from "../../docs/page.js";
import { example } from "../../docs/example.js";
import { controlsTabsPage } from "./page.js";
import snippet from "./page.ts?jfx-code";

export function controlsTabsDoc(): void {
  docPage(
    { title: "Tabs", summary: "tabs() and tab(): a strip of panels, one of them mounted at a time." },
    () => {
      example({ code: snippet }, () => {
        controlsTabsPage();
      });
    }
  );
}
