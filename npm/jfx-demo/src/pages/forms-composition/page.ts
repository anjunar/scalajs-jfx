import { button, classes, div, listProperty, onClick, property } from "@anjunar/jfx-core";
import { arrayForm, form, input, inputContainer, Size, subForm } from "@anjunar/jfx-forms";
import { translated } from "../../app/i18n.js";

class AddressModel {
  @Size(1, 60)
  readonly city = property("");
}

class CompositionModel {
  readonly tags = listProperty<string>(["typescript"]);
  readonly address = property(new AddressModel());
}

export function formsCompositionPage(): void {
  const model = new CompositionModel();

  form(model, () => {
    div(() => {
      classes("flex", "flex-col", "gap-4");

      // arrayForm must be a direct child of form/subForm so it can bind to the
      // parent's ListProperty. A fieldSet intentionally creates a new form
      // context for its children and would therefore hide model.tags.
      div(() => {
        classes("flex", "flex-col", "gap-2");
        arrayForm("tags", (index) => {
          input(`tags-${index}`);
        });
        button(translated("Add tag"), {}, () => {
          classes("px-3", "py-1.5");
          onClick(() => model.tags.add(""));
        });
      });

      subForm("address", model.address.get, () => {
        inputContainer({ label: translated("City").get }, () => {
          input("city");
        });
      });
    });
  });
}
