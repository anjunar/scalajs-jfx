/** Keep title/summary in sync with the "/forms/basics" entry in ../../app/catalog.ts. */
import { docPage } from "../../docs/page.js";
import { example } from "../../docs/example.js";
import { formsBasicsPage } from "./page.js";
import snippet from "./page.ts?jfx-code";

export function formsBasicsDoc(): void {
  docPage(
    {
      title: "Form and model",
      summary: "form(), input(), inputContainer(): a FormModel of Property/ListProperty fields, bound to controls by name.",
    },
    () => {
      example({ code: snippet }, () => {
        formsBasicsPage();
      });
    }
  );
}
