/** Keep title/summary in sync with the "/controls/remote" entry in ../../app/catalog.ts. */
import { text } from "@anjunar/jfx-core";
import type { RouteContext } from "@anjunar/jfx-router";
import { docPage } from "../../docs/page.js";
import { example } from "../../docs/example.js";
import { callout } from "../../docs/callout.js";
import { controlsRemotePage } from "./page.js";
import { translated } from "../../app/i18n.js";
import snippet from "./page.ts?jfx-code";

export function controlsRemoteDoc(context?: RouteContext): void {
  const requestedOffset = Number(context?.queryParams["remote-rows.offset"] ?? 0);
  const initialOffset = Number.isFinite(requestedOffset) && requestedOffset >= 0 ? requestedOffset : 0;

  docPage(
    {
      title: "RemoteSource",
      summary: "remoteSource(): a sparsely loaded data source -- initial, initialQuery, rangeQuery, sortQuery, totalCount, nextQuery -- fed to the same tableView() as a local ListProperty.",
    },
    () => {
      example({ code: snippet }, () => {
        controlsRemotePage(initialOffset);
      });

      callout("note", () => {
        text(
          translated("On a request-aware Node deployment, the query links render the selected page without JavaScript. GitHub Pages serves a fixed prerendered snapshot, so its pager changes rows after hydration.")
        );
      });
    }
  );
}
