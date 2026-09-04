/** Keep title/summary in sync with the "/forms/validation" entry in ../../app/catalog.ts. */
import { text } from "@anjunar/jfx-core";
import { docPage } from "../../docs/page.js";
import { example } from "../../docs/example.js";
import { callout } from "../../docs/callout.js";
import { formsValidationPage } from "./page.js";
import snippet from "./page.ts?jfx-code";

export function formsValidationDoc(): void {
  docPage(
    {
      title: "Validators",
      summary: "All 22 built-in validators, one field each -- notNull() becomes a real reflect.Annotation the unmodified Scala ValidatorFactory/BuiltinValidators consume.",
    },
    () => {
      example({ code: snippet, note: "Type into a field and move on to see its validator's message; the schema is TS-native data, not a ported reimplementation -- see validators.ts." }, () => {
        formsValidationPage();
      });

      callout("note", () => {
        text(
          "Each group above is a fieldSet, which groups controls for error propagation and disabled-state cascading -- it does not bind its children to the model by name; only form/subForm bind. Its own name (\"presence-group\", ...) is deliberately not a model field."
        );
      });
    }
  );
}
