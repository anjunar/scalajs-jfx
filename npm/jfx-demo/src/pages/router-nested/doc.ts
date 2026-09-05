/** Keep title/summary in sync with the "/router/nested" entry in ../../app/catalog.ts. */
import { heading, text } from "@anjunar/jfx-core";
import { docPage } from "../../docs/page.js";
import { example } from "../../docs/example.js";
import { codeBlock } from "../../docs/code-block.js";
import { routerNestedPage } from "./page.js";
import snippet from "./page.ts?jfx-code";
import detailSnippet from "./detail.ts?jfx-code";
import { translated } from "../../app/i18n.js";

export function routerNestedDoc(): void {
  docPage(
    {
      title: "Nested route",
      summary: "routerOutlet() and a child route: the one place today's router already renders one level of nesting.",
    },
    () => {
      example({ code: snippet }, () => {
        routerNestedPage();
      });
      heading(3, () => text(translated("The child route (detail.ts)")));
      codeBlock(detailSnippet);
    }
  );
}
