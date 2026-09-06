/** Keep title/summary in sync with the "/controls/tabs" entry in ../../app/catalog.ts. */
import { docPage } from "../../docs/page.js";
import { example } from "../../docs/example.js";
import { controlsTabsPage } from "./page.js";
import snippet from "./page.ts?jfx-code";

export function controlsTabsDoc(): void {
  docPage(
    { title: "Tabs", summary: "tabs() and tab(): compare active-only lifecycle disposal with state that survives in keep-mounted panels." },
    () => {
      example({ code: snippet }, () => {
        controlsTabsPage();
      });
    }
  );
}
