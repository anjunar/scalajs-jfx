import { property } from "@anjunar/jfx-core";
import { comboBox, form } from "@anjunar/jfx-forms";

export function formsComboBoxPage(): void {
  const model = { color: property<string | null>(null) };

  form(model, {}, () => {
    comboBox("color", { items: ["Red", "Green", "Blue"], placeholder: "Choose one" });
  });
}
