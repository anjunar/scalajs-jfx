/** Keep title/summary in sync with the "/viewport/notification" entry in ../../app/catalog.ts. */
import { docPage } from "../../docs/page.js";
import { example } from "../../docs/example.js";
import { viewportNotificationPage } from "./page.js";
import snippet from "./page.ts?jfx-code";

export function viewportNotificationDoc(): void {
  docPage(
    { title: "Notification", summary: "notify(): a toast mounted into the shared viewport layer, with a kind and a duration." },
    () => {
      example({ code: snippet }, () => {
        viewportNotificationPage();
      });
    }
  );
}
