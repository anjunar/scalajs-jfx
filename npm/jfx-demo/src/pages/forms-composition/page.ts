import { button, classes, div, listProperty, onClick, property } from "@anjunar/jfx-core";
import { arrayForm, form, input, inputContainer, size as sizeValidator, subForm } from "@anjunar/jfx-forms";

export function formsCompositionPage(): void {
  const address = { city: property("") };
  const model = {
    tags: listProperty<string>(["typescript"]),
    address: property(address),
  };

  form(model, {}, () => {
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
        button("Add tag", {}, () => {
          classes("px-3", "py-1.5");
          onClick(() => model.tags.add(""));
        });
      });

      subForm("address", address, { schema: { city: [sizeValidator(1, 60)] } }, () => {
        inputContainer({ label: "City" }, () => {
          input("city");
        });
      });
    });
  });
}
