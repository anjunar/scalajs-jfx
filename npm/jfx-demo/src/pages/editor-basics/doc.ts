/** Keep title/summary in sync with the "/editor/basics" entry in ../../app/catalog.ts. */
import { text } from "@anjunar/jfx-core";
import { docPage } from "../../docs/page.js";
import { example } from "../../docs/example.js";
import { callout } from "../../docs/callout.js";
import { editorBasicsPage } from "./page.js";
import { translated } from "../../app/i18n.js";
import snippet from "./page.ts?jfx-code";

export function editorBasicsDoc(): void {
  docPage(
    {
      title: "Editor",
      summary: "editor(), plugins: a model-bound Lexical editor whose public Markdown value remains observable and replaceable.",
    },
    () => {
      example({ code: snippet }, () => {
        editorBasicsPage();
      });

      callout("note", () => {
        text(translated(
          "plugins is a name list, not an options object: basePlugin()/headingPlugin()/... are " +
            "Scala functions, not values, so jfx-bridge calls the matching one for each name. An " +
            "editor with no plugins still edits rich text -- it just renders no toolbar. The link " +
            "and image plugins open their dialogs as @anjunar/jfx-viewport windows, so an editor " +
            "using either one needs a viewport ancestor -- entry-client.ts/entry-server.ts already " +
            "wrap the whole app in one."
        ));
      });
    }
  );
}
