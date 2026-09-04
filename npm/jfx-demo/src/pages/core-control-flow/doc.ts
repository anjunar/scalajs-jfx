/** Keep title/summary in sync with the "/core/control-flow" entry in ../../app/catalog.ts. */
import { docPage } from "../../docs/page.js";
import { example } from "../../docs/example.js";
import { coreControlFlowPage } from "./page.js";
import snippet from "./page.ts?jfx-code";

export function coreControlFlowDoc(): void {
  docPage(
    {
      title: "when and forEach",
      summary: "when() mounts a body while a condition holds; forEach() reconciles a body per item of a listProperty; classIf() toggles one class reactively.",
    },
    () => {
      example({ code: snippet }, () => {
        coreControlFlowPage();
      });
    }
  );
}
