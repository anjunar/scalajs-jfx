/** Keep title/summary in sync with the "/router/params" entry in ../../app/catalog.ts. */
import { heading, text } from "@anjunar/jfx-core";
import { docPage } from "../../docs/page.js";
import { example } from "../../docs/example.js";
import { codeBlock } from "../../docs/code-block.js";
import { routerParamsPage } from "./page.js";
import snippet from "./page.ts?jfx-code";
import detailSnippet from "./detail.ts?jfx-code";

export function routerParamsDoc(): void {
  docPage(
    {
      title: "Context and concurrency",
      summary: "An asynchronous RouteLoad, RouteContext.params/queryParams/failure, and a digits-only constraint that falls back to onFailure.",
    },
    () => {
      example({ code: snippet }, () => {
        routerParamsPage();
      });
      heading(3, () => text("The child route's loader (detail.ts)"));
      codeBlock(detailSnippet);
    }
  );
}
