/** Keep title/summary in sync with the "/json/mapper" entry in ../../app/catalog.ts. */
import { docPage } from "../../docs/page.js";
import { example } from "../../docs/example.js";
import { jsonMapperPage } from "./page.js";
import snippet from "./page.ts?jfx-code";

export function jsonMapperDoc(): void {
  docPage(
    {
      title: "Schema-driven JSON mapping",
      summary: "Decorators and JsonMapper map a TypeScript class with renamed fields, IDs and ListProperty values.",
    },
    () => {
      example({ code: snippet }, () => {
        jsonMapperPage();
      });
    },
  );
}
