/** Keep title/summary in sync with the "/controls/table" entry in ../../app/catalog.ts. */
import { docPage } from "../../docs/page.js";
import { example } from "../../docs/example.js";
import { callout } from "../../docs/callout.js";
import { text } from "@anjunar/jfx-core";
import { controlsTablePage } from "./page.js";
import snippet from "./page.ts?jfx-code";
import { translated } from "../../app/i18n.js";

export function controlsTableDoc(): void {
  docPage(
    {
      title: "TableView",
      summary: "tableView(), column(): a virtualized table over a local ListProperty, with a sortable column and a crawlable SSR pager.",
    },
    () => {
      example({ code: snippet }, () => {
        controlsTablePage();
      });

      callout("note", () => {
        text(translated("crawlable + crawlId render the page links in the footer on the server, so a crawler without JavaScript can still reach past the first screen."));
      });
    }
  );
}
