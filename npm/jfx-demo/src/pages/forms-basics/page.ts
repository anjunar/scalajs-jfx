import { classes, div, property } from "@anjunar/jfx-core";
import { email as emailValidator, form, input, inputContainer, notBlank } from "@anjunar/jfx-forms";

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
        inputContainer({ label: "Name" }, () => {
          input("name");
        });
        inputContainer({ label: "Email" }, () => {
          input("email", { type: "email" });
        });
      });
    }
  );
}
