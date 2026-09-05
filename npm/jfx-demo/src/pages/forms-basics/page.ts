import { classes, div, property } from "@anjunar/jfx-core";
import { email as emailValidator, form, input, inputContainer, notBlank } from "@anjunar/jfx-forms";
import { translated } from "../../app/i18n.js";

export function formsBasicsPage(): void {
  const model = {
    name: property(""),
    email: property(""),
  };

  form(
    model,
    {
      schema: {
        name: [notBlank()],
        email: [notBlank(), emailValidator()],
      },
    },
    () => {
      div(() => {
        classes("flex", "flex-col", "gap-3");
        inputContainer({ label: translated("Name").get }, () => {
          input("name");
        });
        inputContainer({ label: translated("Email").get }, () => {
          input("email", { type: "email" });
        });
      });
    }
  );
}
