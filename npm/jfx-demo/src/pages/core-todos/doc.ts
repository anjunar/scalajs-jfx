/** Keep title/summary in sync with the "/core/todos" entry in ../../app/catalog.ts. */
import { docPage } from "../../docs/page.js";
import { example } from "../../docs/example.js";
import { coreTodosPage } from "./page.js";
import snippet from "./page.ts?jfx-code";

export function coreTodosDoc(): void {
  docPage(
    {
      title: "Everything together",
      summary: "A small todo list -- property, listProperty, forEach, when, classIf and disposeWith in one page.",
    },
    () => {
      example(
        {
          code: snippet,
          note: "remaining is derived from two independent sources (the list and every item's own done) by hand-subscribing, not with a single map() -- see the comment in the source.",
        },
        () => {
          coreTodosPage();
        }
      );
    }
  );
}
