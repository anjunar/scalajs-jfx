import { button, classes, div, onClick, property, text } from "@anjunar/jfx-core";
import { Email, form, input, inputContainer, NotBlank } from "@anjunar/jfx-forms";
import type { FormHandle } from "@anjunar/jfx-forms";
import { translated } from "../../app/i18n.js";

class AccountModel {
  @NotBlank()
  readonly name = property("Ada Lovelace");

  @NotBlank()
  @Email()
  readonly email = property("ada@example.org");
}

export function formsBasicsPage(): void {
  const model = new AccountModel();
  const validation = property(translated("Ready to validate.").get);
  let formHandle!: FormHandle;

  div(() => {
    classes("flex", "flex-col", "gap-4");

    formHandle = form(model, () => {
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

    div(() => {
      classes("showcase-action-row");
      button(translated("Validate"), {}, () => onClick(() => {
        const errors = formHandle.validate();
        validation.set(errors.length === 0
          ? translated("The model is valid.").get
          : `${errors.length} ${translated("validation issue(s)").get}: ${errors.join(" · ")}`);
      }));
      button(translated("Clear sample"), {}, () => onClick(() => {
        model.name.set("");
        model.email.set("not-an-email");
        validation.set(translated("Invalid sample loaded. Validate to inspect the constraints.").get);
      }));
      button(translated("Restore sample"), {}, () => onClick(() => {
        model.name.set("Ada Lovelace");
        model.email.set("ada@example.org");
        formHandle.clearErrors();
        validation.set(translated("Valid sample restored.").get);
      }));
    });

    div(() => {
      classes("showcase-result");
      div(() => text(validation));
      div(() => text(model.name.map((name) => `${translated("Model name").get}: ${name || "—"}`)));
      div(() => text(model.email.map((email) => `${translated("Model email").get}: ${email || "—"}`)));
    });
  });
}
