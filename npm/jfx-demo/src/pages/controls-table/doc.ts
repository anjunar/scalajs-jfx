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
      summary: "tableView(), column(), remoteSource(): a virtualized table over 1,000 rows, with sortable columns and request-aware SSR ranges.",
    },
    () => {
      example({ code: snippet }, () => {
        controlsTablePage();
      });

      callout("note", () => {
        text(translated("crawlable + crawlId render page links in the server HTML. A request-aware SSR host can render those query URLs without JavaScript; the static GitHub Pages copy applies them after hydration."));
      });
    }
  );
}
