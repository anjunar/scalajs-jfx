/** Keep title/summary in sync with the "/controls/virtual-list" entry in ../../app/catalog.ts. */
import { docPage } from "../../docs/page.js";
import { example } from "../../docs/example.js";
import { controlsVirtualListPage } from "./page.js";
import snippet from "./page.ts?jfx-code";

export function controlsVirtualListDoc(): void {
  docPage(
    {
      title: "VirtualListView",
      summary: "virtualList(): measured row heights, one column, virtualized over a local ListProperty, with a header that scrolls with the rows.",
    },
    () => {
      example({ code: snippet }, () => {
        controlsVirtualListPage();
      });
    }
  );
}
