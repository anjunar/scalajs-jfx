/** Keep title/summary in sync with the "/forms/validation" entry in ../../app/catalog.ts. */
import { text } from "@anjunar/jfx-core";
import { docPage } from "../../docs/page.js";
import { example } from "../../docs/example.js";
import { callout } from "../../docs/callout.js";
import { formsValidationPage } from "./page.js";
import { translated } from "../../app/i18n.js";
import snippet from "./page.ts?jfx-code";

export function formsValidationDoc(): void {
  docPage(
    {
      title: "Validators",
      summary: "All 22 built-in validators, one field each -- TypeScript decorators become annotations for the unchanged Scala validator runtime.",
    },
    () => {
      example({ code: snippet, note: "Type into a field and move on to see its validator's message; each field carries its validator as a TypeScript decorator." }, () => {
        formsValidationPage();
      });

      callout("note", () => {
        text(translated(
          "Number, email and date fields use the corresponding HTML input types. Date validators compare ISO dates with the local calendar date. Boolean selectors bind actual true/false values to AssertTrue and AssertFalse."
        ));
      });
    }
  );
}
