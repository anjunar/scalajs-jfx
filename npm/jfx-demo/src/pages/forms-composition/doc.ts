/** Keep title/summary in sync with the "/forms/composition" entry in ../../app/catalog.ts. */
import { text } from "@anjunar/jfx-core";
import { docPage } from "../../docs/page.js";
import { example } from "../../docs/example.js";
import { callout } from "../../docs/callout.js";
import { formsCompositionPage } from "./page.js";
import snippet from "./page.ts?jfx-code";

export function formsCompositionDoc(): void {
  docPage(
    {
      title: "Composing forms",
      summary: "subForm() and arrayForm(): a nested model object and a repeating field bound to the parent form.",
    },
    () => {
      example({ code: snippet }, () => {
        formsCompositionPage();
      });

      callout("note", () => {
        text(
          "arrayForm must be mounted directly below form or subForm. fieldSet intentionally provides its own grouping context for its children; placing arrayForm inside it would register the array with the fieldSet instead of the parent model, so changes to model.tags could not create new items. The arrayForm itself renders a fieldset and remains the correct grouping boundary here."
        );
      });
    }
  );
}
