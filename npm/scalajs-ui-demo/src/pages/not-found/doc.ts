/** Keep title/summary in sync with the "/404" entry in ../../app/catalog.ts. */
import { docPage } from "../../docs/page.js";
import { notFoundPage } from "./page.js";

export function notFoundDoc(): void {
  docPage(
    { title: "Not found", summary: "An unknown route, answered with its own HTTP status." },
    () => {
      notFoundPage();
    }
  );
}
