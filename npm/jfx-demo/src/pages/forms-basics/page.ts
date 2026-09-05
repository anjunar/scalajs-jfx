import { classes, div, property } from "@anjunar/jfx-core";
import { Email, form, input, inputContainer, NotBlank } from "@anjunar/jfx-forms";
import { translated } from "../../app/i18n.js";

class AccountModel {
  @NotBlank()
  readonly name = property("");

  @NotBlank()
  @Email()
  readonly email = property("");
}

export function formsBasicsPage(): void {
  const model = new AccountModel();

  form(model, () => {
    div(() => {
      classes("flex", "flex-col", "gap-3");
      inputContainer({ label: translated("Name").get }, () => {
        input("name");
      });
      inputContainer({ label: translated("Email").get }, () => {
        input("email", { type: "email" });
      });
    });
  });
}
