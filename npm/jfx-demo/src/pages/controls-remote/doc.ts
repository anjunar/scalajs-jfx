/** Keep title/summary in sync with the "/controls/remote" entry in ../../app/catalog.ts. */
import { text } from "@anjunar/jfx-core";
import type { RouteContext } from "@anjunar/jfx-router";
import { docPage } from "../../docs/page.js";
import { example } from "../../docs/example.js";
import { callout } from "../../docs/callout.js";
import { controlsRemotePage } from "./page.js";
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
          "The footer pager remains usable without JavaScript: its server-rendered links carry remote-rows.offset and remote-rows.limit, and RemoteSource.initialOffset renders the requested page directly on the server. Hydration then adds client-side navigation to those same links -- what sorting exists is not carried across pages, but sorting itself needs a click handler and so is unreachable without JavaScript in the first place."
        );
      });
    }
  );
}
