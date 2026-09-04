/** Keep title/summary in sync with the "/" entry in ../../app/catalog.ts. */
import { docPage } from "../../docs/page.js";
import { homePage } from "./page.js";

export function homeDoc(): void {
  docPage(
    { title: "Overview", summary: "What @anjunar/jfx-* is and where to start." },
    () => {
      homePage();
    }
  );
}
