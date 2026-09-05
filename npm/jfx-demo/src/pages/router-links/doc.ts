/** Keep title/summary in sync with the "/router/links" entry in ../../app/catalog.ts. */
import { code, paragraph, text } from "@anjunar/jfx-core";
import { docPage } from "../../docs/page.js";
import { example } from "../../docs/example.js";
import { callout } from "../../docs/callout.js";
import { routerLinksPage } from "./page.js";
import snippet from "./page.ts?jfx-code";
import { translated } from "../../app/i18n.js";

export function routerLinksDoc(): void {
  docPage(
    {
      title: "routerLink",
      summary: "A navigating anchor with an activeClass -- a real <a href> either way, so navigation works before any JavaScript runs.",
    },
    () => {
      example({ code: snippet }, () => {
        routerLinksPage();
      });

      callout("note", () => {
        paragraph(() => {
          text(translated("RouterConfig also takes a "));
          code(() => text(translated("basePath")));
          text(
            translated(" -- every route and routerLink resolves under it, for mounting the whole app under a URL prefix (a reverse proxy path, say). Not exercised live here: this demo's own routes, nav links and search index all assume no prefix, and setting one would mean rewriting every hardcoded path in this project just to prove the option exists. The option itself is unchanged since CLAUDE_REVIEW_3.md -- see RouterConfig in @anjunar/jfx-router's router.ts.")
          );
        });
      });
    }
  );
}
