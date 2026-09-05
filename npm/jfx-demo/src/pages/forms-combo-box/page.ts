import { property } from "@anjunar/jfx-core";
import { comboBox, form } from "@anjunar/jfx-forms";
import { translated } from "../../app/i18n.js";

export function formsComboBoxPage(): void {
  const model = { color: property<string | null>(null) };

  form(model, {}, () => {
    comboBox("color", {
      items: [translated("Red").get, translated("Green").get, translated("Blue").get],
      placeholder: translated("Choose one").get,
    });
  });
}
