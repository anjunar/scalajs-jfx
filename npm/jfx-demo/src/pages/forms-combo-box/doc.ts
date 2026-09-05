/** Keep title/summary in sync with the "/forms/combo-box" entry in ../../app/catalog.ts. */
import { text } from "@anjunar/jfx-core";
import { docPage } from "../../docs/page.js";
import { example } from "../../docs/example.js";
import { callout } from "../../docs/callout.js";
import { formsComboBoxPage } from "./page.js";
import { translated } from "../../app/i18n.js";
import snippet from "./page.ts?jfx-code";

export function formsComboBoxDoc(): void {
  docPage(
    { title: "ComboBox", summary: "comboBox(), items, placeholder: a searchable single-select bound to a form field." },
    () => {
      example({ code: snippet }, () => {
        formsComboBoxPage();
      });

      callout("note", () => {
        text(translated("The dropdown is an @anjunar/jfx-viewport overlay, so a comboBox needs a viewport ancestor -- entry-client.ts/entry-server.ts already wrap the whole app in one."));
      });
    }
  );
}
